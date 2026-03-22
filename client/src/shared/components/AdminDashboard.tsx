import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/useAuth.ts";
import { useSuccessToast } from "../hooks/useSuccessToast";
import {
    deployElection,
    electionManagement,
    getAllElections,
    startElection,
} from "@services/ElectionService";
import type {DashboardAction, Election, ElectionStatus, PreparedElection} from "@/shared/types";
import "../styles/Dashboard.css";

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

function formatWindow(startIso: string, endIso: string) {
    const formatOptions: Intl.DateTimeFormatOptions = {
        year: "numeric",
        month: "short",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    };

    return `${new Date(startIso).toLocaleString(undefined, formatOptions)} → ${new Date(endIso).toLocaleString(
        undefined,
        formatOptions
    )}`;
}

function actionPriority(action: DashboardAction) {
    switch (action) {
        case "completeVoting":
            return 0;
        case "start":
            return 1;
        case "deploy":
            return 2;
        default:
            return 99;
    }
}

export default function AdminDashboard() {
    const auth = useAuth();
    const { t } = useTranslation("dashboard", { keyPrefix: "admin" });
    const { t: tDashboard } = useTranslation("dashboard");
    const { t: tElections } = useTranslation("elections");
    const { showSuccessToast } = useSuccessToast();
    const [elections, setElections] = useState<Election[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");
    const [actionBusyId, setActionBusyId] = useState<string | null>(null);

    const showErrorToast = useCallback(
        (message: string) => {
            showSuccessToast(message, 5000, "error");
        },
        [showSuccessToast]
    );
    const resolveLifecycleError = useCallback(
        (error: unknown) => {
            const message = electionManagement.asErrorMessage(
                error,
                t("messages.actionFailed", { defaultValue: "Unable to complete this action." })
            );
            if (/election cannot be started before.*configured start\s*time/i.test(message)) {
                return tElections("list.messages.startBeforeConfiguredStartTime", {
                    defaultValue: "This election cannot be started before its configured start time.",
                });
            }
            return message;
        },
        [t, tElections]
    );

    useEffect(() => {
        let cancelled = false;

        async function loadDashboardData() {
            setLoading(true);
            setLoadError("");

            try {
                const data = await getAllElections();
                if (cancelled) return;
                setElections(data);
            } catch (error) {
                if (cancelled) return;
                const message = electionManagement.asErrorMessage(
                    error,
                    t("messages.loadFailed", { defaultValue: "Unable to load dashboard data." })
                );
                setElections([]);
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
    }, [showErrorToast, t]);

    const prepared = useMemo<PreparedElection[]>(() => {
        const now = Date.now();

        return elections.map((election) => {
            const startsAtIso = resolveStartTime(election);
            const startsAtMs = toTimestamp(startsAtIso, toTimestamp(election.endTime));
            const endsAtMs = toTimestamp(election.endTime);
            const updatedAtMs = toTimestamp(election.updatedAt ?? election.createdAt ?? election.endTime);
            const status = computeStatus(now, startsAtMs, endsAtMs);

            let action: DashboardAction = null;
            if (!election.contractAddress && endsAtMs > now) {
                action = "deploy";
            } else if (election.contractAddress && election.phase === "REGISTRATION") {
                action = "start";
            } else if (election.contractAddress && election.phase === "VOTING") {
                action = "completeVoting";
            }

            return {
                ...election,
                startsAtIso,
                startsAtMs,
                endsAtMs,
                updatedAtMs,
                status,
                action,
            };
        });
    }, [elections]);

    const totals = useMemo(
        () => ({
            total: prepared.length,
            open: prepared.filter((item) => item.status === "open").length,
            upcoming: prepared.filter((item) => item.status === "upcoming").length,
            tally: prepared.filter((item) => item.phase === "TALLY").length,
        }),
        [prepared]
    );

    const needsAction = useMemo(
        () =>
            prepared
                .filter((item) => item.action !== null)
                .sort((left, right) => {
                    const priorityDelta = actionPriority(left.action) - actionPriority(right.action);
                    if (priorityDelta !== 0) return priorityDelta;
                    return left.endsAtMs - right.endsAtMs;
                })
                .slice(0, 6),
        [prepared]
    );

    const recent = useMemo(
        () =>
            [...prepared]
                .sort((left, right) => right.updatedAtMs - left.updatedAtMs)
                .slice(0, 5),
        [prepared]
    );

    const replaceElection = useCallback((updated: Election) => {
        setElections((prev) =>
            prev.map((item) => (item.publicId === updated.publicId ? updated : item))
        );
    }, []);

    const handleDeploy = useCallback(
        async (election: PreparedElection) => {
            setActionBusyId(election.publicId);
            try {
                const updated = await deployElection(election.publicId);
                replaceElection(updated);
                showSuccessToast(
                    t("messages.deployed", {
                        title: election.title,
                        defaultValue: `Election deployed: ${election.title}`,
                    })
                );
            } catch (error) {
                showErrorToast(resolveLifecycleError(error));
            } finally {
                setActionBusyId(null);
            }
        },
        [replaceElection, resolveLifecycleError, showErrorToast, showSuccessToast, t]
    );

    const handleStart = useCallback(
        async (election: PreparedElection) => {
            setActionBusyId(election.publicId);
            try {
                const updated = await startElection(election.publicId);
                replaceElection(updated);
                showSuccessToast(
                    t("messages.started", {
                        title: election.title,
                        defaultValue: `Election started: ${election.title}`,
                    })
                );
            } catch (error) {
                showErrorToast(resolveLifecycleError(error));
            } finally {
                setActionBusyId(null);
            }
        },
        [replaceElection, resolveLifecycleError, showErrorToast, showSuccessToast, t]
    );

    const needsActionContent = (() => {
        if (loading) {
            return <p className="dashboard-panel-state">{t("state.loading")}</p>;
        }

        if (needsAction.length === 0) {
            return <p className="dashboard-panel-state">{t("empty.needsAction")}</p>;
        }

        return (
            <div className="dashboard-item-list">
                {needsAction.map((election) => (
                    <article key={`action-${election.publicId}`} className="dashboard-item">
                        <div>
                            <h3 className="dashboard-item-title">{election.title}</h3>
                            <p className="dashboard-item-meta">
                                {formatWindow(election.startsAtIso, election.endTime)}
                            </p>
                            <div className="dashboard-item-badges">
                                <span
                                    className={`dashboard-status-pill dashboard-status-pill-${election.status}`}
                                >
                                    {tDashboard(`status.${election.status}`)}
                                </span>
                                <span className="dashboard-phase-pill">
                                    {tDashboard(`phase.${election.phase}`)}
                                </span>
                            </div>
                        </div>

                        <div className="dashboard-item-actions">
                            {election.action === "deploy" ? (
                                <button
                                    type="button"
                                    className="dashboard-small-button"
                                    onClick={() => void handleDeploy(election)}
                                    disabled={actionBusyId === election.publicId}
                                >
                                    {t("actions.deploy")}
                                </button>
                            ) : null}
                            {election.action === "start" ? (
                                <button
                                    type="button"
                                    className="dashboard-small-button"
                                    onClick={() => void handleStart(election)}
                                    disabled={actionBusyId === election.publicId}
                                >
                                    {t("actions.start")}
                                </button>
                            ) : null}
                            {election.action === "completeVoting" ? (
                                <Link to="/admin/elections" className="dashboard-small-button">
                                    {t("actions.completeVoting")}
                                </Link>
                            ) : null}

                            <Link to={`/admin/elections/${election.publicId}`} className="dashboard-small-button secondary">
                                {t("actions.details")}
                            </Link>
                        </div>
                    </article>
                ))}
            </div>
        );
    })();

    const recentContent = (() => {
        if (loading) {
            return <p className="dashboard-panel-state">{t("state.loading")}</p>;
        }

        if (recent.length === 0) {
            return <p className="dashboard-panel-state">{t("empty.recent")}</p>;
        }

        return (
            <div className="dashboard-item-list">
                {recent.map((election) => (
                    <article key={`recent-${election.publicId}`} className="dashboard-item">
                        <div>
                            <h3 className="dashboard-item-title">{election.title}</h3>
                            <p className="dashboard-item-meta">
                                {formatWindow(election.startsAtIso, election.endTime)}
                            </p>
                        </div>
                        <div className="dashboard-item-actions">
                            <span
                                className={`dashboard-status-pill dashboard-status-pill-${election.status}`}
                            >
                                {tDashboard(`status.${election.status}`)}
                            </span>
                            <Link to={`/admin/elections/${election.publicId}`} className="dashboard-small-button secondary">
                                {t("actions.details")}
                            </Link>
                        </div>
                    </article>
                ))}
            </div>
        );
    })();

    if (auth.status !== "authenticated") return null;

    const welcomeName = auth.user.name ?? auth.user.username ?? auth.user.email ?? "Admin";

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
                        <span className="dashboard-kpi-label">{t("kpis.total")}</span>
                        <strong className="dashboard-kpi-value">{totals.total}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.open")}</span>
                        <strong className="dashboard-kpi-value">{totals.open}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.upcoming")}</span>
                        <strong className="dashboard-kpi-value">{totals.upcoming}</strong>
                    </article>
                    <article className="dashboard-kpi-card">
                        <span className="dashboard-kpi-label">{t("kpis.tally")}</span>
                        <strong className="dashboard-kpi-value">{totals.tally}</strong>
                    </article>
                </section>

                <div className="dashboard-content-grid">
                    <section className="dashboard-panel">
                        <div className="dashboard-panel-header">
                            <h2 className="dashboard-panel-title">{t("sections.needsAction")}</h2>
                            <Link to="/admin/elections" className="dashboard-panel-link">
                                {t("actions.viewAllElections")}
                            </Link>
                        </div>

                        {needsActionContent}
                    </section>

                    <section className="dashboard-panel">
                        <h2 className="dashboard-panel-title">{t("sections.quickLinks")}</h2>
                        <div className="dashboard-link-list">
                            <Link to="/admin/elections/create" className="dashboard-action-link">
                                {t("actions.createElection")}
                            </Link>
                            <Link to="/admin/parties" className="dashboard-action-link">
                                {t("actions.manageParties")}
                            </Link>
                            <Link to="/admin/results" className="dashboard-action-link">
                                {t("actions.viewResults")}
                            </Link>
                        </div>
                    </section>
                </div>

                <section className="dashboard-panel">
                    <h2 className="dashboard-panel-title">{t("sections.recent")}</h2>
                    {recentContent}
                </section>
            </div>
        </div>
    );
}
