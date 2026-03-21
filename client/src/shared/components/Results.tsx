import React, { useEffect, useMemo, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
    ELECTION_VAULT_MIN_PASSWORD_LENGTH,
    base64ToHex,
    decryptElectionPayload,
    derivePublicKeyFingerprint,
    parseStoredElectionKeyVault,
    unlockElectionPrivateKeyAsCryptoKey,
} from "@/crypto/electionKeys";
import { loadElectionKeyVault, saveElectionKeyVault } from "@/crypto/electionKeyVaultStorage";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { electionManagement, getAllElections } from "../services/ElectionService";
import {
    getElectionResults,
    getTallyBallots,
    publishElectionResults,
    resultsManagement,
} from "../services/ResultsService";
import type { Election } from "../types/election";
import type { ElectionResult } from "../types/result";

import "../styles/Dashboard.css";
import "../styles/Results.css";

type ResultEntry = {
    election: Election;
    result: ElectionResult | null;
    error?: string;
};

const ballotDecoder = new TextDecoder();

function formatDateTime(value?: string | null): string {
    if (!value) {
        return "Date unavailable";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return "Date unavailable";
    }

    return parsed.toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function formatNumber(value: number): string {
    return value.toLocaleString("en-US");
}

function formatPercentage(value: number): string {
    return `${value.toFixed(1)}%`;
}

function base64ToBytes(value: string): Uint8Array {
    const binary = globalThis.atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
}

function extractCandidatePublicId(plaintext: Uint8Array): string {
    let parsed: unknown;
    try {
        parsed = JSON.parse(ballotDecoder.decode(plaintext));
    } catch {
        throw new Error("A decrypted ballot could not be parsed as JSON.");
    }

    if (
        typeof parsed !== "object" ||
        parsed === null ||
        typeof (parsed as { candidatePublicId?: unknown }).candidatePublicId !== "string"
    ) {
        throw new Error("A decrypted ballot does not contain a candidate selection.");
    }

    const candidatePublicId = (parsed as { candidatePublicId: string }).candidatePublicId.trim();
    if (!candidatePublicId) {
        throw new Error("A decrypted ballot contains an empty candidate selection.");
    }

    return candidatePublicId;
}

const Results: React.FC = () => {
    const location = useLocation();
    const { showSuccessToast } = useSuccessToast();
    const isAdminView = location.pathname.startsWith("/admin/");
    const electionLinkBase = isAdminView ? "/admin/elections" : "/citizen/elections";

    const [entries, setEntries] = useState<ResultEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");
    const [expandedElection, setExpandedElection] = useState<string | null>(null);
    const [publishingElectionId, setPublishingElectionId] = useState<string | null>(null);
    const [vaultPasswords, setVaultPasswords] = useState<Record<string, string>>({});
    const [backupJsons, setBackupJsons] = useState<Record<string, string>>({});
    const [publishErrors, setPublishErrors] = useState<Record<string, string>>({});

    useEffect(() => {
        let cancelled = false;

        async function loadResults() {
            setLoading(true);
            setLoadError("");

            try {
                const elections = await getAllElections();
                const tallyElections = elections
                    .filter((election) => election.phase === "TALLY")
                    .sort((left, right) => Date.parse(right.endTime) - Date.parse(left.endTime));

                const settled = await Promise.allSettled(
                    tallyElections.map(async (election) => ({
                        election,
                        result: await getElectionResults(election.publicId),
                    }))
                );

                if (cancelled) {
                    return;
                }

                setEntries(
                    settled.map((item, index) => {
                        if (item.status === "fulfilled") {
                            return {
                                election: item.value.election,
                                result: item.value.result,
                            };
                        }

                        return {
                            election: tallyElections[index],
                            result: null,
                            error: resultsManagement.asErrorMessage(
                                item.reason,
                                "Unable to load results for this election."
                            ),
                        };
                    })
                );
            } catch (error) {
                if (cancelled) {
                    return;
                }

                setEntries([]);
                setLoadError(
                    electionManagement.asErrorMessage(
                        error,
                        "Unable to load election results right now."
                    )
                );
            } finally {
                if (!cancelled) {
                    setLoading(false);
                }
            }
        }

        void loadResults();

        return () => {
            cancelled = true;
        };
    }, []);

    useEffect(() => {
        if (entries.length === 0) {
            setExpandedElection(null);
            return;
        }

        if (!expandedElection || !entries.some((entry) => entry.election.publicId === expandedElection)) {
            setExpandedElection(entries[0].election.publicId);
        }
    }, [entries, expandedElection]);

    const subtitle = useMemo(() => {
        if (isAdminView) {
            return "TALLY elections appear here. Unlock the election vault locally to publish any encrypted results that are still pending.";
        }

        return "Final election results appear here once the tally has been published.";
    }, [isAdminView]);

    const handlePublish = async (entry: ResultEntry) => {
        const result = entry.result;
        const electionPublicId = entry.election.publicId;

        if (!result) {
            return;
        }

        const vaultPassword = vaultPasswords[electionPublicId] ?? "";
        if (!vaultPassword) {
            setPublishErrors((prev) => ({
                ...prev,
                [electionPublicId]: "Election vault password is required to publish results.",
            }));
            return;
        }

        if (vaultPassword.length < ELECTION_VAULT_MIN_PASSWORD_LENGTH) {
            setPublishErrors((prev) => ({
                ...prev,
                [electionPublicId]: `Vault password must be at least ${ELECTION_VAULT_MIN_PASSWORD_LENGTH} characters.`,
            }));
            return;
        }

        if (!entry.election.encryptionPublicKey) {
            setPublishErrors((prev) => ({
                ...prev,
                [electionPublicId]: "Election encryption public key is missing.",
            }));
            return;
        }

        setPublishingElectionId(electionPublicId);
        setPublishErrors((prev) => ({ ...prev, [electionPublicId]: "" }));

        try {
            const storedRecord = backupJsons[electionPublicId]?.trim()
                ? parseStoredElectionKeyVault(backupJsons[electionPublicId].trim())
                : loadElectionKeyVault(electionPublicId);

            if (!storedRecord) {
                throw new Error(
                    "Election key vault not found on this browser. Paste the backup JSON created during election setup."
                );
            }

            if (storedRecord.electionPublicId !== electionPublicId) {
                throw new Error("The provided key backup does not belong to this election.");
            }

            const electionPublicKeyHex = base64ToHex(
                entry.election.encryptionPublicKey,
                "encryptionPublicKey"
            );
            if (storedRecord.publicKeyHex !== electionPublicKeyHex) {
                throw new Error("Election key backup does not match the configured encryption public key.");
            }

            const privateKey = await unlockElectionPrivateKeyAsCryptoKey(vaultPassword, storedRecord.vault);
            const publicKeyFingerprint = await derivePublicKeyFingerprint(electionPublicKeyHex);
            const expectedCandidateIds = new Set(
                result.candidates.map((candidate) => candidate.candidatePublicId)
            );
            const ballots = await getTallyBallots(electionPublicId);

            const assignments = await Promise.all(
                ballots.map(async (ballot) => {
                    if (!ballot.ciphertext) {
                        throw new Error(`Ballot ${ballot.ballotId} is missing ciphertext.`);
                    }

                    const plaintext = await decryptElectionPayload(
                        base64ToBytes(ballot.ciphertext),
                        privateKey,
                        {
                            electionId: electionPublicId,
                            publicKeyFingerprint,
                        }
                    );

                    const candidatePublicId = extractCandidatePublicId(plaintext);
                    if (!expectedCandidateIds.has(candidatePublicId)) {
                        throw new Error(
                            `Ballot ${ballot.ballotId} resolves to an unknown candidate identifier.`
                        );
                    }

                    return {
                        ballotId: ballot.ballotId,
                        candidatePublicId,
                    };
                })
            );

            const updatedResult = await publishElectionResults(electionPublicId, { assignments });
            saveElectionKeyVault(storedRecord);

            setEntries((prev) =>
                prev.map((item) =>
                    item.election.publicId === electionPublicId
                        ? {
                              ...item,
                              result: updatedResult,
                              error: undefined,
                          }
                        : item
                )
            );
            setPublishErrors((prev) => {
                const next = { ...prev };
                delete next[electionPublicId];
                return next;
            });
            showSuccessToast(`Results published for ${entry.election.title}.`);
        } catch (error) {
            const message = resultsManagement.asErrorMessage(
                error,
                "Unable to publish results for this election."
            );
            setPublishErrors((prev) => ({
                ...prev,
                [electionPublicId]: message,
            }));
            showSuccessToast(message, 5000, "error");
        } finally {
            setPublishingElectionId(null);
        }
    };

    const toggleExpand = (electionId: string) => {
        setExpandedElection((current) => (current === electionId ? null : electionId));
    };

    const publishedCount = entries.filter((entry) => entry.result?.published).length;

    return (
        <div className="dashboard-page">
            <div className="dashboard-card results-card">
                <h2 className="dashboard-title">Results</h2>
                <p className="dashboard-subtitle">
                    {subtitle}
                    {entries.length > 0 ? ` ${publishedCount} of ${entries.length} tally elections are published.` : ""}
                </p>

                {loading ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#6b7280" }}>Loading election results...</div>
                    </div>
                ) : loadError ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#b42318" }}>{loadError}</div>
                    </div>
                ) : entries.length === 0 ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#6b7280" }}>No elections are in the tally stage yet.</div>
                    </div>
                ) : (
                    <div className="results-list">
                        {entries.map((entry) => {
                            const result = entry.result;
                            const electionId = entry.election.publicId;
                            const isExpanded = expandedElection === electionId;
                            const publishError = publishErrors[electionId];
                            const isPublishing = publishingElectionId === electionId;

                            let statusLabel = "Unavailable";
                            let statusClass = "results-status--error";
                            if (result?.published) {
                                statusLabel = "Published";
                                statusClass = "results-status--final";
                            } else if (result) {
                                statusLabel = "Pending tally";
                                statusClass = "results-status--preliminary";
                            }

                            const hasVotes = (result?.totalVotes ?? 0) > 0;

                            return (
                                <div key={electionId} className="results-item">
                                    <button
                                        className="results-item-header"
                                        onClick={() => toggleExpand(electionId)}
                                        aria-expanded={isExpanded}
                                    >
                                        <div className="results-header-left">
                                            <h3 className="results-election-name">{entry.election.title}</h3>
                                            <span className="results-date">
                                                Ended: {formatDateTime(result?.endTime ?? entry.election.endTime)}
                                            </span>
                                        </div>
                                        <div className="results-header-right">
                                            <span className={`results-status ${statusClass}`}>{statusLabel}</span>
                                            <span className="results-expand-icon">
                                                {isExpanded ? "▼" : "▶"}
                                            </span>
                                        </div>
                                    </button>

                                    {isExpanded ? (
                                        <div className="results-details">
                                            <div className="results-meta-row">
                                                <Link
                                                    className="results-election-link"
                                                    to={`${electionLinkBase}/${electionId}`}
                                                >
                                                    Open election
                                                </Link>
                                            </div>

                                            {entry.error || !result ? (
                                                <div className="results-error-box">
                                                    {entry.error ?? "Results are not available for this election."}
                                                </div>
                                            ) : (
                                                <>
                                                    <div className="results-stats">
                                                        <div className="results-stat">
                                                            <span className="results-stat-label">
                                                                Total ballots
                                                            </span>
                                                            <span className="results-stat-value">
                                                                {formatNumber(result.totalVotes)}
                                                            </span>
                                                        </div>
                                                        <div className="results-stat">
                                                            <span className="results-stat-label">
                                                                Tallied ballots
                                                            </span>
                                                            <span className="results-stat-value">
                                                                {formatNumber(result.talliedBallots)} / {formatNumber(result.totalVotes)}
                                                            </span>
                                                        </div>
                                                        <div className="results-stat">
                                                            <span className="results-stat-label">
                                                                Registered voters
                                                            </span>
                                                            <span className="results-stat-value">
                                                                {formatNumber(result.registeredVoters)}
                                                            </span>
                                                        </div>
                                                        <div className="results-stat">
                                                            <span className="results-stat-label">Turnout</span>
                                                            <span className="results-stat-value">
                                                                {formatPercentage(result.turnoutPercentage)}
                                                            </span>
                                                        </div>
                                                    </div>

                                                    {!result.published ? (
                                                        <div className="results-pending-panel">
                                                            <h4 className="results-section-title">
                                                                Results not published yet
                                                            </h4>
                                                            <p className="results-pending-copy">
                                                                Ballots remain encrypted until the tally is completed.
                                                                {isAdminView
                                                                    ? " Publish the results from this browser by unlocking the election vault locally."
                                                                    : " Check back once an administrator finalizes the tally."}
                                                            </p>

                                                            {isAdminView ? (
                                                                <div className="results-publish-panel">
                                                                    <label className="results-form-field">
                                                                        <span>Election vault password</span>
                                                                        <input
                                                                            type="password"
                                                                            autoComplete="current-password"
                                                                            minLength={ELECTION_VAULT_MIN_PASSWORD_LENGTH}
                                                                            value={vaultPasswords[electionId] ?? ""}
                                                                            onChange={(event) =>
                                                                                setVaultPasswords((prev) => ({
                                                                                    ...prev,
                                                                                    [electionId]: event.target.value,
                                                                                }))
                                                                            }
                                                                        />
                                                                    </label>

                                                                    <label className="results-form-field">
                                                                        <span>Backup JSON (optional)</span>
                                                                        <textarea
                                                                            rows={8}
                                                                            value={backupJsons[electionId] ?? ""}
                                                                            onChange={(event) =>
                                                                                setBackupJsons((prev) => ({
                                                                                    ...prev,
                                                                                    [electionId]: event.target.value,
                                                                                }))
                                                                            }
                                                                            placeholder="Paste the saved election key backup if this browser does not already have it."
                                                                        />
                                                                    </label>

                                                                    {publishError ? (
                                                                        <div className="results-publish-error">
                                                                            {publishError}
                                                                        </div>
                                                                    ) : null}

                                                                    <div className="results-publish-actions">
                                                                        <button
                                                                            className="results-publish-button"
                                                                            onClick={() => void handlePublish(entry)}
                                                                            disabled={isPublishing}
                                                                        >
                                                                            {isPublishing
                                                                                ? "Publishing results..."
                                                                                : "Decrypt and publish results"}
                                                                        </button>
                                                                    </div>
                                                                </div>
                                                            ) : null}
                                                        </div>
                                                    ) : (
                                                        <>
                                                            {!hasVotes ? (
                                                                <div className="results-empty-box">
                                                                    No ballots were cast in this election.
                                                                </div>
                                                            ) : null}

                                                            <div className="results-candidates">
                                                                {result.candidates.map((candidate, index) => {
                                                                    const isWinner =
                                                                        hasVotes &&
                                                                        index === 0 &&
                                                                        candidate.votes > 0;

                                                                    return (
                                                                        <div
                                                                            key={candidate.candidatePublicId}
                                                                            className={`results-candidate ${isWinner ? "results-candidate--winner" : ""}`}
                                                                        >
                                                                            <div className="results-candidate-info">
                                                                                <span className="results-candidate-rank">
                                                                                    {isWinner ? "Winner" : `#${index + 1}`}
                                                                                </span>
                                                                                <div className="results-candidate-copy">
                                                                                    <span className="results-candidate-name">
                                                                                        {candidate.fullName || "Unnamed candidate"}
                                                                                    </span>
                                                                                    {candidate.partyName ? (
                                                                                        <span className="results-candidate-party">
                                                                                            {candidate.partyName}
                                                                                        </span>
                                                                                    ) : null}
                                                                                </div>
                                                                            </div>
                                                                            <div className="results-candidate-votes">
                                                                                <div className="results-progress-bar">
                                                                                    <div
                                                                                        className="results-progress-fill"
                                                                                        style={{
                                                                                            width: `${Math.max(candidate.percentage, 0)}%`,
                                                                                        }}
                                                                                    />
                                                                                </div>
                                                                                <span className="results-votes-count">
                                                                                    {formatNumber(candidate.votes)} (
                                                                                    {formatPercentage(candidate.percentage)})
                                                                                </span>
                                                                            </div>
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                        </>
                                                    )}
                                                </>
                                            )}
                                        </div>
                                    ) : null}
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Results;
