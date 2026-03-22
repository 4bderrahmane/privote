import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/useAuth.ts";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { electionManagement, getAllElections } from "@services/ElectionService";
import { getMyRegistrationByElectionPublicId } from "@services/VoterRegistrationService";
import { loadMyVoteReceipts, type StoredVoteReceipt } from "@services/MyVoteReceiptStorage";
import type { Election, ElectionStatus } from "@/shared/types";
import type { VoterRegistration } from "../types/voterRegistration";
import "../styles/Dashboard.css";

type PreparedElection = Election & {
    startsAtIso: string;
    startsAtMs: number;
    endsAtMs: number;
    status: ElectionStatus;
};

function resolveStartTime(election: Election) {
    return election.startTime ?? election.createdAt ?? election.endTime;
}

function toTimestamp(value: string | null | undefined, fallback = 0) {
    const parsed = Date.parse(value ?? "");
    return Number.isFinite(parsed) ? parsed : fallback;
}

function computeStatus(now: number, startsAt: number, endsAt: number): ElectionStatus {
    if (now < startsAt) return "upcoming";
    if (now >= startsAt && now <= endsAt) return "open";
    return "closed";
}

function formatDate(value: string | null | undefined) {
    if (!value) return "—";
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "—";

    return parsed.toLocaleString(undefined, {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function truncateHash(value: string | null | undefined) {
    if (!value) return "—";
    if (value.length <= 18) return value;
    return `${value.slice(0, 10)}...${value.slice(-6)}`;
}

function registrationState(registration: VoterRegistration | null) {
    if (!registration) return "missing";
    if (registration.participationStatus === "CAST") return "cast";
    if (registration.commitmentStatus === "ON_CHAIN") return "onChain";
    return "pending";
}

export default function CitizenDashboard() {
    const auth = useAuth();
    const { t } = useTranslation("dashboard", { keyPrefix: "citizen" });
    const { t: tDashboard } = useTranslation("dashboard");
    const { showSuccessToast } = useSuccessToast();
    const [elections, setElections] = useState<Election[]>([]);
    const [registrations, setRegistrations] = useState<Record<string, VoterRegistration | null>>({});
    const [receipts, setReceipts] = useState<StoredVoteReceipt[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");

    const showErrorToast = useCallback(
        (message: string) => {
            showSuccessToast(message, 5000, "error");
        },
        [showSuccessToast]
    );

    const userId = auth.status === "authenticated" ? auth.user.id : null;

    useEffect(() => {
        const activeUserId = userId;
        if (!activeUserId) {
            setElections([]);
            setRegistrations({});
            setReceipts([]);
            setLoadError("");
            setLoading(false);
            return;
        }
        const resolvedUserId: string = activeUserId;

        let cancelled = false;

        async function loadDashboardData() {
            setLoading(true);
            setLoadError("");

            try {
                const localReceipts = loadMyVoteReceipts(resolvedUserId);
                if (cancelled) return;
                setReceipts(localReceipts);

                const data = await getAllElections();
                if (cancelled) return;
                setElections(data);

                const relevant = data.filter(
                    (election) => election.phase === "REGISTRATION" || election.phase === "VOTING"
                );
                const registrationResults = await Promise.allSettled(
                    relevant.map((election) => getMyRegistrationByElectionPublicId(election.publicId))
                );
                if (cancelled) return;

                const nextRegistrations: Record<string, VoterRegistration | null> = {};
                let partialFailure = false;

                for (let index = 0; index < relevant.length; index += 1) {
                    const result = registrationResults[index];
                    const election = relevant[index];
                    if (result.status === "fulfilled") {
                        nextRegistrations[election.publicId] = result.value;
                    } else {
                        partialFailure = true;
                    }
                }

                setRegistrations(nextRegistrations);
                if (partialFailure) {
                    showErrorToast(
                        t("messages.registrationLoadPartial", {
                            defaultValue: "Some registration statuses could not be loaded.",
                        })
                    );
                }
            } catch (error) {
                if (cancelled) return;
                const message = electionManagement.asErrorMessage(
                    error,
                    t("messages.loadFailed", { defaultValue: "Unable to load dashboard data." })
                );
                setElections([]);
                setRegistrations({});
                setLoadError(message);
                showErrorToast(message);
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        void loadDashboardData();

        return () => {
            cancelled = true;
        };
    }, [showErrorToast, t, userId]);

    const prepared = useMemo<PreparedElection[]>(() => {
        const now = Date.now();

        return elections.map((election) => {
            const startsAtIso = resolveStartTime(election);
            const startsAtMs = toTimestamp(startsAtIso, toTimestamp(election.endTime));
            const endsAtMs = toTimestamp(election.endTime);
            const status = computeStatus(now, startsAtMs, endsAtMs);

            return {
                ...election,
                startsAtIso,
                startsAtMs,
                endsAtMs,
                status,
            };
        });
    }, [elections]);

    const votedElectionIds = useMemo(() => {
        const values = new Set<string>();
        for (const receipt of receipts) {
            values.add(receipt.electionPublicId);
        }
        for (const [electionPublicId, registration] of Object.entries(registrations)) {
            if (registration?.participationStatus === "CAST") {
                values.add(electionPublicId);
            }
        }
        return values;
    }, [receipts, registrations]);

    const kpis = useMemo(
        () => ({
            openToVote: prepared.filter(
                (election) => election.status === "open" && election.phase === "VOTING"
            ).length,
            upcoming: prepared.filter((election) => election.status === "upcoming").length,
            registered: Object.values(registrations).filter(
                (registration) => registration?.commitmentStatus === "ON_CHAIN"
            ).length,
            voted: votedElectionIds.size,
        }),
        [prepared, registrations, votedElectionIds.size]
    );

    const nextElection = useMemo(() => {
        const candidates = prepared.filter(
            (election) => election.status === "open" || election.status === "upcoming"
        );

        if (candidates.length === 0) return null;

        return [...candidates].sort((left, right) => {
            const leftRank = left.status === "open" ? 0 : 1;
            const rightRank = right.status === "open" ? 0 : 1;
            if (leftRank !== rightRank) return leftRank - rightRank;
            return left.startsAtMs - right.startsAtMs;
        })[0];
    }, [prepared]);

    const nextElectionRegistration = nextElection
        ? registrations[nextElection.publicId] ?? null
        : null;

    let nextElectionActionLabel = t("nextElection.closedAction");
    if (nextElection) {
        nextElectionActionLabel = t("nextElection.upcomingAction");
        if (nextElection.status === "open" && nextElection.phase === "VOTING") {
            nextElectionActionLabel = t("nextElection.openAction");
        }
    }

    const recentReceipts = receipts.slice(0, 3);

    if (auth.status !== "authenticated") return null;

    const welcomeName = auth.user.name ?? auth.user.username ?? auth.user.email ?? "Citizen";

    let nextElectionPanelContent = <p className="dashboard-panel-state">{t("state.loading")}</p>;
    if (!loading) {
        if (nextElection === null) {
            nextElectionPanelContent = <p className="dashboard-panel-state">{t("nextElection.none")}</p>;
        } else {
            let nextElectionStatusLabel = t("nextElection.statusUpcoming");
            if (nextElection.status === "open") {
                nextElectionStatusLabel = t("nextElection.statusOpen");
            }

            nextElectionPanelContent = (
                <div className="dashboard-next-election">
                    <h3 className="dashboard-item-title">{nextElection.title}</h3>
                    <p className="dashboard-item-meta">
                        {formatDate(nextElection.startsAtIso)} → {formatDate(nextElection.endTime)}
                    </p>
                    <div className="dashboard-item-badges">
                        <span
                            className={`dashboard-status-pill dashboard-status-pill-${nextElection.status}`}
                        >
                            {nextElectionStatusLabel}
                        </span>
                        <span className="dashboard-phase-pill">
                            {tDashboard(`phase.${nextElection.phase}`)}
                        </span>
                    </div>
                    <p className="dashboard-panel-state">
                        {t("nextElection.registrationHint", {
                            status: t(`registration.${registrationState(nextElectionRegistration)}`),
                        })}
                    </p>
                    <div className="dashboard-item-actions">
                        <Link
                            to={`/citizen/elections/${nextElection.publicId}`}
                            className="dashboard-small-button"
                        >
                            {nextElectionActionLabel}
                        </Link>
                    </div>
                </div>
            );
        }
    }

    let recentVotesContent = <p className="dashboard-panel-state">{t("state.loading")}</p>;
    if (!loading) {
        if (recentReceipts.length === 0) {
            recentVotesContent = <p className="dashboard-panel-state">{t("recentVotes.empty")}</p>;
        } else {
            recentVotesContent = (
                <div className="dashboard-item-list">
                    {recentReceipts.map((receipt) => (
                        <article key={receipt.ballotId} className="dashboard-item">
                            <div>
                                <h3 className="dashboard-item-title">{receipt.electionTitle}</h3>
                                <p className="dashboard-item-meta">
                                    {t("recentVotes.votedAt", { date: formatDate(receipt.castAt) })}
                                </p>
                            </div>
                            <div className="dashboard-item-actions">
                                <code className="dashboard-hash">{truncateHash(receipt.transactionHash)}</code>
                                <Link
                                    to={`/citizen/elections/${receipt.electionPublicId}`}
                                    className="dashboard-small-button secondary"
                                >
                                    {t("nextElection.upcomingAction")}
                                </Link>
                            </div>
                        </article>
                    ))}
                </div>
            );
        }
    }

    return (
        <div className="dashboard-page dashboard-page-command">
            <div className="dashboard-shell">
                <section className="dashboard-hero">
                    <h1 className="dashboard-hero-title">{t("title")}</h1>
                    <p className="dashboard-hero-subtitle">{t("subtitle", { name: welcomeName })}</p>
                    {loadError ? <p className="dashboard-inline-error">{loadError}</p> : null}
                </section>

                <section className="dashboard-kpi-grid">
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.openToVote")}</span>
                        <strong className="dashboard-kpi-value">{kpis.openToVote}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.upcoming")}</span>
                        <strong className="dashboard-kpi-value">{kpis.upcoming}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.registered")}</span>
                        <strong className="dashboard-kpi-value">{kpis.registered}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.voted")}</span>
                        <strong className="dashboard-kpi-value">{kpis.voted}</strong>
                    </article>
                </section>

                <div className="dashboard-content-grid">
                    <section className="dashboard-panel">
                        <h2 className="dashboard-panel-title">{t("sections.nextElection")}</h2>
                        {nextElectionPanelContent}
                    </section>

                    <section className="dashboard-panel">
                        <h2 className="dashboard-panel-title">{t("sections.quickLinks")}</h2>
                        <div className="dashboard-link-list">
                            <Link to="/citizen/elections" className="dashboard-action-link">
                                {t("quickLinks.browse")}
                            </Link>
                            <Link to="/citizen/my-votes" className="dashboard-action-link">
                                {t("quickLinks.myVotes")}
                            </Link>
                            <Link to="/citizen/results" className="dashboard-action-link">
                                {t("quickLinks.results")}
                            </Link>
                        </div>
                    </section>
                </div>

                <section className="dashboard-panel">
                    <div className="dashboard-panel-header">
                        <h2 className="dashboard-panel-title">{t("sections.recentVotes")}</h2>
                        <Link to="/citizen/my-votes" className="dashboard-panel-link">
                            {t("recentVotes.viewAll")}
                        </Link>
                    </div>

                    {recentVotesContent}
                </section>
            </div>
        </div>
    );
}
