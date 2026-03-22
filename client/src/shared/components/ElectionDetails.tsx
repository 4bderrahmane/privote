import {type ChangeEvent, type FormEvent, useCallback, useEffect, useMemo, useState} from "react";
import {Link, useParams} from "react-router-dom";
import {useTranslation} from "react-i18next";
import {useAuth} from "@/auth/useAuth.ts";
import {isAdminUser} from "@/auth/roles.ts";
import {base64ToHex, derivePublicKeyFingerprint, encryptElectionPayload,} from "@/crypto/electionKeys";
import {
    createVaultAndElectionIdentity,
    deriveElectionIdentityFromVault,
    electionKeyFromExternalNullifier,
} from "@/semaphore/identity";
import {getSemaphoreSnarkArtifacts, SEMAPHORE_ARTIFACT_DEPTH} from "@/semaphore/artifacts";
import {createElectionVoteProofViaFastify} from "@/semaphore/proof";
import {hasIdentityVault, loadIdentityVault, saveIdentityVault} from "@/semaphore/identityVaultStorage";
import {useSuccessToast} from "../hooks/useSuccessToast";
import {
    candidateManagement,
    createCandidateForElection,
    getActiveCandidatesByElectionPublicId,
    getCandidatesByElectionPublicId,
} from "@services/CandidateService";
import {electionManagement, getElectionByPublicId} from "@services/ElectionService";
import {getAllParties, partyManagement} from "@services/PartyService";
import {
    getMyRegistrationByElectionPublicId,
    registerMyCommitment,
    voterRegistrationManagement,
} from "@services/VoterRegistrationService";
import {castMyVote, voteManagement} from "@services/VoteService";
import {saveMyVoteReceipt} from "@services/MyVoteReceiptStorage";
import {
    type Candidate,
    type CandidateForm,
    type CandidateStatus,
    INITIAL_CANDIDATE_FORM,
    STATUS_ORDER
} from "@shared-types/candidate";
import type {Election, ElectionStatus} from "@/shared/types";
import type {Party} from "@shared-types/party";
import type {VoterRegistration} from "@shared-types/voterRegistration";
import "../styles/ElectionDetails.css";

function resolveStartTime(election: Election) {
    return election.startTime ?? election.createdAt ?? election.endTime;
}

function computeStatus(now: number, startsAt: number, endsAt: number): ElectionStatus {
    if (now < startsAt) return "upcoming";
    if (now >= startsAt && now <= endsAt) return "open";
    return "closed";
}

function formatDateTime(value: string | null | undefined, fallback: string) {
    if (!value) return fallback;

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return fallback;
    }

    return parsed.toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function sortCandidates(candidates: Candidate[]) {
    return [...candidates].sort((left, right) => {
        const statusDelta = STATUS_ORDER[left.status] - STATUS_ORDER[right.status];
        if (statusDelta !== 0) return statusDelta;
        return left.fullName.localeCompare(right.fullName, undefined, {sensitivity: "base"});
    });
}

function formatStatusLabel(status: string) {
    return status
        .toLowerCase()
        .split("_")
        .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
        .join(" ");
}

function formatCandidateStatus(status: CandidateStatus) {
    return formatStatusLabel(status);
}

function statusTone(status: CandidateStatus) {
    switch (status) {
        case "ACTIVE":
            return "active";
        case "PENDING_APPROVAL":
            return "pending";
        case "WITHDRAWN":
            return "withdrawn";
        case "DISQUALIFIED":
            return "disqualified";
        default:
            return "pending";
    }
}

function formatWorkflowStatus(status: string) {
    return formatStatusLabel(status);
}

function safeHasIdentityVault() {
    try {
        return hasIdentityVault();
    } catch {
        return false;
    }
}

function resolveProofServiceBaseUrl() {
    const configured = import.meta.env.VITE_PROOF_SERVICE_BASE_URL;
    if (configured && configured.trim().length > 0) {
        return configured.replace(/\/+$/, "");
    }
    return "http://127.0.0.1:4010";
}

export default function ElectionDetails() { // NOSONAR
    const {electionId} = useParams<{ electionId: string }>();
    const {t} = useTranslation("elections", {keyPrefix: "detailsPage"});
    const auth = useAuth();
    const {showSuccessToast} = useSuccessToast();
    const isAdmin = auth.status === "authenticated" && isAdminUser(auth.user);
    const showErrorToast = useCallback(
        (message: string) => {
            showSuccessToast(message, 5000, "error");
        },
        [showSuccessToast]
    );

    const [election, setElection] = useState<Election | null>(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");
    const [candidates, setCandidates] = useState<Candidate[]>([]);
    const [candidateLoading, setCandidateLoading] = useState(true);
    const [parties, setParties] = useState<Party[]>([]);
    const [form, setForm] = useState<CandidateForm>(INITIAL_CANDIDATE_FORM);
    const [savingCandidate, setSavingCandidate] = useState(false);
    const [registration, setRegistration] = useState<VoterRegistration | null>(null);
    const [registrationLoading, setRegistrationLoading] = useState(!isAdmin);
    const [identityPassword, setIdentityPassword] = useState("");
    const [identityPasswordConfirm, setIdentityPasswordConfirm] = useState("");
    const [workflowBusy, setWorkflowBusy] = useState<"register" | "vote" | null>(null);
    const [selectedCandidatePublicId, setSelectedCandidatePublicId] = useState("");
    const [identityVaultPresent, setIdentityVaultPresent] = useState(() => safeHasIdentityVault());

    useEffect(() => {
        let cancelled = false;

        async function loadElection() {
            if (!electionId) {
                setLoadError(t("errors.missingUuid", {defaultValue: "Election UUID is missing."}));
                setLoading(false);
                return;
            }

            setLoading(true);
            setLoadError("");

            try {
                const data = await getElectionByPublicId(electionId);
                if (cancelled) return;
                setElection(data);
            } catch (error) {
                if (cancelled) return;
                setLoadError(
                    electionManagement.asErrorMessage(
                        error,
                        t("errors.loadFailed", {defaultValue: "Unable to load election details."})
                    )
                );
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        void loadElection();

        return () => {
            cancelled = true;
        };
    }, [electionId, t]);

    useEffect(() => {
        let cancelled = false;

        async function loadCandidateContext() {
            if (!electionId) {
                setCandidateLoading(false);
                return;
            }

            setCandidateLoading(true);

            try {
                const [loadedCandidates, loadedParties] = await Promise.all([
                    isAdmin
                        ? getCandidatesByElectionPublicId(electionId)
                        : getActiveCandidatesByElectionPublicId(electionId),
                    isAdmin ? getAllParties() : Promise.resolve([]),
                ]);

                if (cancelled) return;
                setCandidates(sortCandidates(loadedCandidates));
                setParties(loadedParties);
            } catch (error) {
                if (cancelled) return;
                const fallback = isAdmin
                    ? t("candidates.errors.loadFailed", {
                        defaultValue: "Unable to load candidates and supporting data.",
                    })
                    : t("candidates.errors.loadFailed", {
                        defaultValue: "Unable to load candidates.",
                    });
                const message = partyManagement.asErrorMessage(
                    error,
                    candidateManagement.asErrorMessage(error, fallback)
                );
                setCandidates([]);
                setParties([]);
                showErrorToast(message);
            } finally {
                if (!cancelled) setCandidateLoading(false);
            }
        }

        void loadCandidateContext();

        return () => {
            cancelled = true;
        };
    }, [electionId, isAdmin, showErrorToast, t]);

    useEffect(() => {
        let cancelled = false;

        async function loadRegistration() {
            if (isAdmin || !electionId) {
                setRegistrationLoading(false);
                return;
            }

            setRegistrationLoading(true);

            try {
                const data = await getMyRegistrationByElectionPublicId(electionId);
                if (cancelled) return;
                setRegistration(data);
            } catch (error) {
                if (cancelled) return;
                showErrorToast(
                    voterRegistrationManagement.asErrorMessage(
                        error,
                        t("vote.errors.registrationLoadFailed", {
                            defaultValue: "Unable to load your registration status for this election.",
                        })
                    )
                );
            } finally {
                if (!cancelled) setRegistrationLoading(false);
            }
        }

        void loadRegistration();

        return () => {
            cancelled = true;
        };
    }, [electionId, isAdmin, showErrorToast, t]);

    useEffect(() => {
        if (candidates.length === 0) {
            setSelectedCandidatePublicId("");
            return;
        }

        setSelectedCandidatePublicId((current) => {
            if (current && candidates.some((candidate) => candidate.publicId === current)) {
                return current;
            }
            return candidates[0].publicId;
        });
    }, [candidates]);

    const handleFormChange = (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
        const {name, value} = event.target;
        setForm((prev) => ({...prev, [name]: value}));
    };

    const handleCandidateSubmit = async (event: FormEvent) => {
        event.preventDefault();

        if (!election) {
            return;
        }

        if (!form.citizenCin.trim()) {
            showErrorToast(
                t("candidates.errors.cinRequired", {
                    defaultValue: "Citizen CIN is required.",
                })
            );
            return;
        }

        setSavingCandidate(true);

        try {
            const created = await createCandidateForElection(election.publicId, {
                citizenCin: form.citizenCin,
                partyPublicId: form.partyPublicId || undefined,
                status: form.status,
            });

            setCandidates((prev) => sortCandidates([...prev, created]));
            setForm(INITIAL_CANDIDATE_FORM);
            showSuccessToast(
                t("candidates.messages.created", {
                    name: created.fullName,
                    title: election.title,
                    defaultValue: `Candidate "${created.fullName}" was added to ${election.title}.`,
                })
            );
        } catch (error) {
            showErrorToast(
                candidateManagement.asErrorMessage(
                    error,
                    t("candidates.errors.submitFailed", {
                        defaultValue: "Unable to add candidate to this election.",
                    })
                )
            );
        } finally {
            setSavingCandidate(false);
        }
    };

    const selectedCandidate = useMemo(
        () => candidates.find((candidate) => candidate.publicId === selectedCandidatePublicId) ?? null,
        [candidates, selectedCandidatePublicId]
    );

    async function deriveElectionIdentity() {
        if (!election?.externalNullifier) {
            throw new Error(
                t("vote.errors.externalNullifierMissing", {
                    defaultValue: "Election external nullifier is missing.",
                })
            );
        }

        if (!identityPassword) {
            throw new Error(
                t("vote.errors.passwordRequired", {
                    defaultValue: "Identity password is required.",
                })
            );
        }

        const electionKey = electionKeyFromExternalNullifier(election.externalNullifier);
        const storedVault = loadIdentityVault();

        if (!storedVault) {
            if (!identityPasswordConfirm) {
                throw new Error(
                    t("vote.errors.passwordConfirmRequired", {
                        defaultValue: "Confirm your identity password to create a local voter vault.",
                    })
                );
            }

            if (identityPassword !== identityPasswordConfirm) {
                throw new Error(
                    t("vote.errors.passwordMismatch", {
                        defaultValue: "Identity password confirmation does not match.",
                    })
                );
            }

            const created = await createVaultAndElectionIdentity(identityPassword, electionKey);
            saveIdentityVault(created.vault);
            setIdentityVaultPresent(true);
            return {identity: created.identity, createdVault: true};
        }

        return {
            identity: await deriveElectionIdentityFromVault(identityPassword, storedVault, electionKey),
            createdVault: false,
        };
    }

    const handleRegister = async () => {
        if (!election) {
            return;
        }

        setWorkflowBusy("register");

        try {
            const {identity, createdVault} = await deriveElectionIdentity();
            const response = await registerMyCommitment(election.publicId, identity.commitment.toString());
            setRegistration(response);
            showSuccessToast(
                createdVault
                    ? t("vote.messages.registeredWithVault", {
                        title: election.title,
                        defaultValue: `Local voter identity created and registered for ${election.title}.`,
                    })
                    : t("vote.messages.registered", {
                        title: election.title,
                        defaultValue: `Voting identity registered for ${election.title}.`,
                    })
            );
        } catch (error) {
            showErrorToast(
                voterRegistrationManagement.asErrorMessage(
                    error,
                    t("vote.errors.registrationSubmitFailed", {
                        defaultValue: "Unable to register your voting identity for this election.",
                    })
                )
            );
        } finally {
            setWorkflowBusy(null);
        }
    };

    const handleVoteSubmit = async (event: FormEvent) => {
        event.preventDefault();

        if (!election) {
            return;
        }

        if (registration?.commitmentStatus !== "ON_CHAIN") {
            showErrorToast(
                t("vote.errors.registrationRequired", {
                    defaultValue: "Complete voter registration before casting a vote.",
                })
            );
            return;
        }

        if (registration.participationStatus === "CAST") {
            showErrorToast(
                t("vote.errors.alreadyCast", {
                    defaultValue: "You have already submitted a ballot for this election.",
                })
            );
            return;
        }

        if (!selectedCandidate) {
            showErrorToast(
                t("vote.errors.candidateRequired", {
                    defaultValue: "Select a candidate before submitting your ballot.",
                })
            );
            return;
        }

        if (!election.contractAddress) {
            showErrorToast(
                t("vote.errors.contractMissing", {
                    defaultValue: "Election contract address is missing.",
                })
            );
            return;
        }

        if (!election.encryptionPublicKey) {
            showErrorToast(
                t("vote.errors.publicKeyMissing", {
                    defaultValue: "Election encryption public key is missing.",
                })
            );
            return;
        }

        setWorkflowBusy("vote");

        try {
            const {identity} = await deriveElectionIdentity();
            if (identity.commitment.toString() !== registration.identityCommitment) {
                throw new Error(
                    t("vote.errors.identityMismatch", {
                        defaultValue:
                            "The unlocked local voter identity does not match the identity registered for this election.",
                    })
                );
            }

            const electionPublicKeyHex = base64ToHex(election.encryptionPublicKey, "encryptionPublicKey");
            const publicKeyFingerprint = await derivePublicKeyFingerprint(electionPublicKeyHex);
            const ballotPlaintext = JSON.stringify({
                candidatePublicId: selectedCandidate.publicId,
            });
            const ciphertext = await encryptElectionPayload(
                new TextEncoder().encode(ballotPlaintext),
                electionPublicKeyHex,
                {
                    electionId: election.publicId,
                    publicKeyFingerprint,
                }
            );
            const proof = await createElectionVoteProofViaFastify({
                fastifyBaseUrl: resolveProofServiceBaseUrl(),
                electionAddress: election.contractAddress,
                identity,
                ciphertext,
                externalNullifier: election.externalNullifier ?? "",
                snarkArtifacts: getSemaphoreSnarkArtifacts(),
                circuitDepth: SEMAPHORE_ARTIFACT_DEPTH,
            });

            const receipt = await castMyVote(election.publicId, {
                ciphertext,
                nullifier: proof.nullifier.toString(),
                proof: proof.points.map((point) => point.toString()),
            });

            if (auth.status === "authenticated") {
                saveMyVoteReceipt(auth.user.id, election.title, receipt);
            }

            setRegistration((prev) =>
                prev
                    ? {
                        ...prev,
                        participationStatus: "CAST",
                    }
                    : prev
            );
            showSuccessToast(
                t("vote.messages.cast", {
                    name: selectedCandidate.fullName,
                    txHash: receipt.transactionHash,
                    defaultValue: `Ballot submitted for ${selectedCandidate.fullName}.`,
                })
            );
        } catch (error) {
            showErrorToast(
                voteManagement.asErrorMessage(
                    error,
                    voterRegistrationManagement.asErrorMessage(
                        error,
                        t("vote.errors.castFailed", {
                            defaultValue: "Unable to cast your vote for this election.",
                        })
                    )
                )
            );
        } finally {
            setWorkflowBusy(null);
        }
    };

    if (loading) {
        return (
            <div className="election-details-page">
                <div className="election-details-state">
                    <h1>{t("loadingTitle", {defaultValue: "Loading election"})}</h1>
                </div>
            </div>
        );
    }

    if (loadError) {
        return (
            <div className="election-details-page">
                <div className="election-details-state">
                    <h1>{t("loadErrorTitle", {defaultValue: "Unable to load election"})}</h1>
                    <p>{loadError}</p>
                    <Link className="election-details-back" to=".." relative="path">
                        {t("back", {defaultValue: "Back"})}
                    </Link>
                </div>
            </div>
        );
    }

    if (!election) {
        return (
            <div className="election-details-page">
                <div className="election-details-state">
                    <h1>{t("notFoundTitle", {defaultValue: "Election not found"})}</h1>
                    <Link className="election-details-back" to=".." relative="path">
                        {t("back", {defaultValue: "Back"})}
                    </Link>
                </div>
            </div>
        );
    }

    const startTime = resolveStartTime(election);
    const status = computeStatus(
        Date.now(),
        new Date(startTime).getTime(),
        new Date(election.endTime).getTime()
    );
    const statusClass = `election-details-badge election-details-badge-${status}`;
    const fallbackText = t("fallback", {defaultValue: "Not available"});
    const canManageCandidates = isAdmin && election.phase === "REGISTRATION";
    const voterCanRegister = !isAdmin && election.phase === "REGISTRATION";
    const voterCanCast =
        !isAdmin &&
        election.phase === "VOTING" &&
        registration?.commitmentStatus === "ON_CHAIN" &&
        registration.participationStatus !== "CAST";
    const alreadyRegisteredForRegistrationPhase =
        election.phase === "REGISTRATION" &&
        voterCanRegister === false &&
        registration?.commitmentStatus === "ON_CHAIN";

    let candidateListContent = (
        <div className="election-details-banner">
            {t("candidates.loading", {defaultValue: "Loading candidates..."})}
        </div>
    );
    if (!candidateLoading) {
        if (candidates.length === 0) {
            candidateListContent = (
                <div className="election-details-banner">
                    {t("candidates.empty", {
                        defaultValue: "No candidates have been assigned to this election yet.",
                    })}
                </div>
            );
        } else {
            candidateListContent = (
                <div className="election-candidate-list">
                    {candidates.map((candidate) => (
                        <article key={candidate.publicId} className="election-candidate-card">
                            <div className="election-candidate-head">
                                <div>
                                    <h3 className="election-candidate-name">{candidate.fullName}</h3>
                                    <p className="election-candidate-meta">
                                        {candidate.partyName ||
                                            t("candidates.partyIndependent", {
                                                defaultValue: "Independent candidate",
                                            })}
                                    </p>
                                </div>

                                <span
                                    className={`election-candidate-status election-candidate-status-${statusTone(candidate.status)}`}
                                >
                                    {t(`candidates.status.${candidate.status}`, {
                                        defaultValue: formatCandidateStatus(candidate.status),
                                    })}
                                </span>
                            </div>
                        </article>
                    ))}
                </div>
            );
        }
    }

    let registerActionLabel = t("vote.registerAction", {defaultValue: "Register to vote"});
    if (workflowBusy === "register") {
        registerActionLabel = t("vote.registering", {defaultValue: "Registering identity..."});
    } else if (registration?.commitmentStatus === "ON_CHAIN") {
        registerActionLabel = t("vote.refreshRegistration", {defaultValue: "Refresh registration"});
    }

    let votingContent = null;
    if (election.phase === "VOTING") {
        if (registration?.participationStatus === "CAST") {
            votingContent = (
                <div className="election-details-banner">
                    {t("vote.alreadyCast", {
                        defaultValue: "Your ballot has already been submitted for this election.",
                    })}
                </div>
            );
        } else if (registration?.commitmentStatus === "ON_CHAIN") {
            votingContent = (
                <form className="election-vote-form" onSubmit={handleVoteSubmit}>
                    <div className="election-vote-options">
                        {candidates.map((candidate) => {
                            const checked = candidate.publicId === selectedCandidatePublicId;
                            return (
                                <label
                                    key={candidate.publicId}
                                    className={`election-vote-option ${checked ? "is-selected" : ""}`}
                                >
                                    <input
                                        type="radio"
                                        name="candidatePublicId"
                                        value={candidate.publicId}
                                        checked={checked}
                                        aria-label={candidate.fullName}
                                        onChange={() => setSelectedCandidatePublicId(candidate.publicId)}
                                    />
                                    <div>
                                        <strong>{candidate.fullName}</strong>
                                        <span>
                                            {candidate.partyName ||
                                                t("candidates.partyIndependent", {
                                                    defaultValue: "Independent candidate",
                                                })}
                                        </span>
                                    </div>
                                </label>
                            );
                        })}
                    </div>

                    <div className="election-voter-actions">
                        <button
                            type="submit"
                            className="primary-button"
                            disabled={!voterCanCast || workflowBusy !== null}
                        >
                            {workflowBusy === "vote"
                                ? t("vote.casting", {defaultValue: "Submitting ballot..."})
                                : t("vote.castAction", {defaultValue: "Cast vote"})}
                        </button>
                    </div>
                </form>
            );
        } else {
            votingContent = (
                <div className="election-details-note">
                    {t("vote.registrationRequiredNote", {
                        defaultValue:
                            "You cannot vote yet because your identity commitment was not registered on-chain during the registration phase.",
                    })}
                </div>
            );
        }
    }

    return (
        <div className="election-details-page">
            <article className="election-details-card">
                <header className="election-details-header">
                    <div>
                        <p className="election-details-kicker">{t("kicker", {defaultValue: "Election"})}</p>
                        <h1 className="election-details-title">{election.title}</h1>
                        <p className="election-details-description">
                            {election.description?.trim() ||
                                t("descriptionFallback", {
                                    defaultValue: "No description was provided for this election.",
                                })}
                        </p>
                    </div>

                    <Link className="election-details-back" to=".." relative="path">
                        {t("back", {defaultValue: "Back"})}
                    </Link>
                </header>

                <div className="election-details-badges">
                    <span className={statusClass}>{t(`status.${status}`, {defaultValue: status})}</span>
                    <span className="election-details-badge election-details-badge-phase">
                        {t(`phase.${election.phase}`, {defaultValue: election.phase})}
                    </span>
                </div>

                <dl className="election-details-grid">
                    <div className="election-details-item">
                        <dt>{t("fields.uuid", {defaultValue: "Election UUID"})}</dt>
                        <dd className="election-details-code">{election.publicId}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.startTime", {defaultValue: "Start time"})}</dt>
                        <dd>{formatDateTime(election.startTime, fallbackText)}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.endTime", {defaultValue: "End time"})}</dt>
                        <dd>{formatDateTime(election.endTime, fallbackText)}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.createdAt", {defaultValue: "Created at"})}</dt>
                        <dd>{formatDateTime(election.createdAt, fallbackText)}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.updatedAt", {defaultValue: "Updated at"})}</dt>
                        <dd>{formatDateTime(election.updatedAt, fallbackText)}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.contractAddress", {defaultValue: "Contract address"})}</dt>
                        <dd className="election-details-code">{election.contractAddress || fallbackText}</dd>
                    </div>

                    <div className="election-details-item">
                        <dt>{t("fields.externalNullifier", {defaultValue: "External nullifier"})}</dt>
                        <dd className="election-details-code">{election.externalNullifier || fallbackText}</dd>
                    </div>

                    <div className="election-details-item election-details-item-wide">
                        <dt>{t("fields.encryptionPublicKey", {defaultValue: "Encryption public key"})}</dt>
                        <dd className="election-details-code">{election.encryptionPublicKey || fallbackText}</dd>
                    </div>
                </dl>

                <section className="election-details-section">
                    <div className="election-details-section-header">
                        <div>
                            <p className="election-details-section-kicker">
                                {isAdmin
                                    ? t("candidates.kickerAdmin", {defaultValue: "Candidate management"})
                                    : t("candidates.kickerCitizen", {defaultValue: "Ballot slate"})}
                            </p>
                            <h2 className="election-details-section-title">
                                {t("candidates.title", {defaultValue: "Candidates"})}
                            </h2>
                            <p className="election-details-section-subtitle">
                                {isAdmin
                                    ? t("candidates.subtitleAdmin", {
                                        defaultValue:
                                            "Assign citizens to this election by CIN. The public interface will only show their name, party, and status.",
                                    })
                                    : t("candidates.subtitleCitizen", {
                                        defaultValue: "Only ACTIVE candidates are shown to citizens.",
                                    })}
                            </p>
                        </div>

                        <div className="election-details-pill">
                            {t(candidates.length === 1 ? "candidates.countOne" : "candidates.countOther", {
                                count: candidates.length,
                                defaultValue: candidates.length === 1 ? "{{count}} candidate" : "{{count}} candidates",
                            })}
                        </div>
                    </div>

                    {isAdmin ? (
                        <form className="election-candidate-form" onSubmit={handleCandidateSubmit}>
                            <fieldset disabled={!canManageCandidates || savingCandidate}>
                                <div className="election-candidate-form-grid">
                                    <label className="election-candidate-field">
                                        <span>{t("candidates.fields.cin", {defaultValue: "Citizen CIN"})}</span>
                                        <input
                                            name="citizenCin"
                                            value={form.citizenCin}
                                            onChange={handleFormChange}
                                            placeholder={t("candidates.placeholders.cin", {
                                                defaultValue: "National ID / CIN",
                                            })}
                                        />
                                    </label>

                                    <label className="election-candidate-field">
                                        <span>{t("candidates.fields.party", {defaultValue: "Party"})}</span>
                                        <select
                                            name="partyPublicId"
                                            value={form.partyPublicId}
                                            onChange={handleFormChange}
                                        >
                                            <option value="">
                                                {t("candidates.partyIndependent", {defaultValue: "Independent candidate"})}
                                            </option>
                                            {parties.map((party) => (
                                                <option key={party.publicId} value={party.publicId}>
                                                    {party.name}
                                                </option>
                                            ))}
                                        </select>
                                    </label>

                                    <label className="election-candidate-field">
                                        <span>{t("candidates.fields.status", {defaultValue: "Status"})}</span>
                                        <select name="status" value={form.status} onChange={handleFormChange}>
                                            <option value="ACTIVE">
                                                {t("candidates.status.ACTIVE", {defaultValue: "Active"})}
                                            </option>
                                            <option value="PENDING_APPROVAL">
                                                {t("candidates.status.PENDING_APPROVAL", {
                                                    defaultValue: "Pending approval",
                                                })}
                                            </option>
                                        </select>
                                    </label>
                                </div>
                            </fieldset>

                            {canManageCandidates ? null : (
                                <div className="election-details-note">
                                    {t("candidates.locked", {
                                        defaultValue:
                                            "Candidate management is locked once the election leaves REGISTRATION.",
                                    })}
                                </div>
                            )}

                            <div className="election-candidate-form-actions">
                                <button
                                    type="submit"
                                    className="primary-button"
                                    disabled={!canManageCandidates || savingCandidate}
                                >
                                    {savingCandidate
                                        ? t("candidates.saving", {defaultValue: "Adding candidate..."})
                                        : t("candidates.add", {defaultValue: "Add candidate"})}
                                </button>
                            </div>
                        </form>
                    ) : null}

                    {candidateListContent}
                </section>

                {isAdmin ? null : (
                    <section className="election-details-section">
                        <div className="election-details-section-header">
                            <div>
                                <p className="election-details-section-kicker">
                                    {t("vote.kicker", {defaultValue: "Citizen workflow"})}
                                </p>
                                <h2 className="election-details-section-title">
                                    {t("vote.title", {defaultValue: "Your participation"})}
                                </h2>
                                <p className="election-details-section-subtitle">
                                    {t("vote.subtitle", {
                                        defaultValue:
                                            "Register your private voting identity during registration, then unlock it to cast your encrypted ballot during voting.",
                                    })}
                                </p>
                            </div>

                            <div className="election-details-pill">
                                {registration?.participationStatus
                                    ? formatWorkflowStatus(registration.participationStatus)
                                    : t("vote.notRegistered", {defaultValue: "Not registered"})}
                            </div>
                        </div>

                        <div className="election-voter-status-grid">
                            <div className="election-voter-status-card">
                                <dt>{t("vote.fields.localVault", {defaultValue: "Local identity vault"})}</dt>
                                <dd>{identityVaultPresent ? t("vote.vaultPresent", {defaultValue: "Present on this browser"}) : t("vote.vaultMissing", {defaultValue: "Missing on this browser"})}</dd>
                            </div>
                            <div className="election-voter-status-card">
                                <dt>{t("vote.fields.commitmentStatus", {defaultValue: "Commitment status"})}</dt>
                                <dd>
                                    {registration?.commitmentStatus
                                        ? formatWorkflowStatus(registration.commitmentStatus)
                                        : fallbackText}
                                </dd>
                            </div>
                            <div className="election-voter-status-card election-voter-status-card-wide">
                                <dt>{t("vote.fields.identityCommitment", {defaultValue: "Registered identity commitment"})}</dt>
                                <dd className="election-details-code">{registration?.identityCommitment || fallbackText}</dd>
                            </div>
                        </div>

                        {registrationLoading ? (
                            <div className="election-details-banner">
                                {t("vote.loadingRegistration", {defaultValue: "Loading your registration status..."})}
                            </div>
                        ) : null}

                        <div className="election-voter-form">
                            <div className="election-voter-form-grid">
                                <label className="election-candidate-field">
                                    <span>{t("vote.fields.password", {defaultValue: "Identity password"})}</span>
                                    <input
                                        type="password"
                                        autoComplete="current-password"
                                        value={identityPassword}
                                        onChange={(event) => setIdentityPassword(event.target.value)}
                                        placeholder={
                                            identityVaultPresent
                                                ? t("vote.placeholders.passwordUnlock", {
                                                    defaultValue: "Unlock your local voter identity",
                                                })
                                                : t("vote.placeholders.passwordCreate", {
                                                    defaultValue: "Create a password for your local voter identity",
                                                })
                                        }
                                    />
                                </label>

                                {identityVaultPresent ? null : (
                                    <label className="election-candidate-field">
                                        <span>{t("vote.fields.passwordConfirm", {defaultValue: "Confirm identity password"})}</span>
                                        <input
                                            type="password"
                                            autoComplete="new-password"
                                            value={identityPasswordConfirm}
                                            onChange={(event) => setIdentityPasswordConfirm(event.target.value)}
                                            placeholder={t("vote.placeholders.passwordConfirm", {
                                                defaultValue: "Repeat the same password",
                                            })}
                                        />
                                    </label>
                                )}
                            </div>

                            <div className="election-details-note">
                                {identityVaultPresent
                                    ? t("vote.vaultHintPresent", {
                                        defaultValue:
                                            "This browser already stores your encrypted voter identity vault. Use the same password you used when you first registered.",
                                    })
                                    : t("vote.vaultHintMissing", {
                                        defaultValue:
                                            "No local voter identity vault was found. Registering now will create one in this browser and reuse it for voting.",
                                    })}
                            </div>
                        </div>

                        {voterCanRegister ? (
                            <div className="election-voter-actions">
                                <button
                                    type="button"
                                    className="primary-button"
                                    onClick={() => void handleRegister()}
                                    disabled={workflowBusy !== null}
                                >
                                    {registerActionLabel}
                                </button>
                            </div>
                        ) : null}

                        {votingContent}

                        {alreadyRegisteredForRegistrationPhase ? (
                            <div className="election-details-banner">
                                {t("vote.registeredReady", {
                                    defaultValue: "Your private voting identity is already registered for this election.",
                                })}
                            </div>
                        ) : null}

                        {election.phase === "TALLY" ? (
                            <div className="election-details-banner">
                                {t("vote.tallyClosed", {
                                    defaultValue: "Voting is closed. This election is currently in tally.",
                                })}
                            </div>
                        ) : null}
                    </section>
                )}
            </article>
        </div>
    );
}
