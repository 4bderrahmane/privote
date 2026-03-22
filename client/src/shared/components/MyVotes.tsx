import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/useAuth.ts";
import { getAllElections, electionManagement } from "@services/ElectionService";
import { loadMyVoteReceipts, type StoredVoteReceipt } from "@services/MyVoteReceiptStorage";
import { getMyRegistrationByElectionPublicId } from "@services/VoterRegistrationService";

import "../styles/Dashboard.css";
import "../styles/MyVotes.css";
import { useSuccessToast } from "../hooks/useSuccessToast";
import type {VoteRecord} from "@shared-types/vote.ts";


function formatDate(value: string | null | undefined, locale: string, unavailableLabel: string): string {
    if (!value) {
        return unavailableLabel;
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return unavailableLabel;
    }

    return parsed.toLocaleDateString(locale, {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function truncateHash(hash: string | null | undefined, unavailableLabel: string): string {
    if (!hash) {
        return unavailableLabel;
    }

    if (hash.length <= 20) {
        return hash;
    }

    return `${hash.slice(0, 10)}...${hash.slice(-8)}`;
}

function buildConfirmedRecord(receipt: StoredVoteReceipt): VoteRecord {
    return {
        id: receipt.ballotId,
        electionId: receipt.electionPublicId,
        electionName: receipt.electionTitle,
        votedAt: receipt.castAt,
        transactionHash: receipt.transactionHash,
        ciphertextHash: receipt.ciphertextHash,
        nullifier: receipt.nullifier,
        blockNumber: receipt.blockNumber ?? null,
        status: "confirmed",
        receiptAvailable: true,
    };
}

const MyVotes: React.FC = () => {
    const { t, i18n } = useTranslation("dashboard");
    const auth = useAuth();
    const { showSuccessToast } = useSuccessToast();
    const userId = auth.status === "authenticated" ? auth.user.id : null;
    const [records, setRecords] = useState<VoteRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");

    const locale = i18n.resolvedLanguage || i18n.language || "en";
    const notAvailable = t("myVotesPage.values.notAvailable");
    const notAvailableOnBrowser = t("myVotesPage.values.notAvailableOnBrowser");
    const transactionHashLabel = t("myVotesPage.labels.transactionHash");
    const ciphertextHashLabel = t("myVotesPage.labels.ciphertextHash");
    const nullifierLabel = t("myVotesPage.labels.nullifier");

    useEffect(() => {
        if (!userId) {
            return;
        }

        let cancelled = false;
        const authenticatedUserId = userId;

        async function loadVotes() {
            setLoading(true);
            setLoadError("");

            const localReceipts = loadMyVoteReceipts(authenticatedUserId);
            const recordsByElection = new Map<string, VoteRecord>(
                localReceipts.map((receipt) => [receipt.electionPublicId, buildConfirmedRecord(receipt)])
            );

            try {
                const elections = await getAllElections();
                const registrationResults = await Promise.allSettled(
                    elections.map(async (election) => ({
                        election,
                        registration: await getMyRegistrationByElectionPublicId(election.publicId),
                    }))
                );

                if (cancelled) {
                    return;
                }

                for (const result of registrationResults) {
                    if (result.status !== "fulfilled") {
                        continue;
                    }

                    const { election, registration } = result.value;
                    if (registration?.participationStatus !== "CAST") {
                        continue;
                    }

                    const existing = recordsByElection.get(election.publicId);
                    if (existing) {
                        recordsByElection.set(election.publicId, {
                            ...existing,
                            electionName: election.title,
                        });
                        continue;
                    }

                    recordsByElection.set(election.publicId, {
                        id: `cast-${election.publicId}`,
                        electionId: election.publicId,
                        electionName: election.title,
                        status: "recorded",
                        receiptAvailable: false,
                    });
                }
            } catch (error) {
                if (cancelled) {
                    return;
                }

                if (recordsByElection.size === 0) {
                    setLoadError(
                        electionManagement.asErrorMessage(
                            error,
                            t("myVotesPage.messages.loadFailed")
                        )
                    );
                }
            }

            if (cancelled) {
                return;
            }

            const nextRecords = Array.from(recordsByElection.values()).sort((left, right) => {
                const leftTime = Date.parse(left.votedAt ?? "");
                const rightTime = Date.parse(right.votedAt ?? "");
                return (Number.isFinite(rightTime) ? rightTime : 0) - (Number.isFinite(leftTime) ? leftTime : 0);
            });

            setRecords(nextRecords);
            setLoading(false);
        }

        void loadVotes();

        return () => {
            cancelled = true;
        };
    }, [userId, t]);

    const displayedRecords = userId ? records : [];
    const displayedLoading = userId ? loading : false;
    const displayedLoadError = userId ? loadError : "";

    const subtitle =
        displayedRecords.length === 0
            ? t("myVotesPage.subtitleEmpty")
            : t("myVotesPage.subtitleWithRecords");

    const copyToClipboard = async (label: string, text?: string | null) => {
        if (!text) {
            return;
        }

        try {
            await navigator.clipboard.writeText(text);
            showSuccessToast(t("myVotesPage.messages.copied", { label }));
        } catch (error) {
            console.error("Failed to copy value:", error);
            showSuccessToast(t("myVotesPage.messages.copyFailed", { label }), 3000, "error");
        }
    };

    let content: React.ReactNode;
    if (displayedLoading) {
        content = (
            <div className="dashboard-status" style={{ paddingTop: 0 }}>
                <div style={{ color: "#6b7280" }}>{t("myVotesPage.state.loading")}</div>
            </div>
        );
    } else if (displayedLoadError) {
        content = (
            <div className="dashboard-status" style={{ paddingTop: 0 }}>
                <div style={{ color: "#b42318" }}>{displayedLoadError}</div>
            </div>
        );
    } else if (displayedRecords.length === 0) {
        content = (
            <div className="dashboard-status" style={{ paddingTop: 0 }}>
                <div style={{ color: "#6b7280" }}>{t("myVotesPage.state.empty")}</div>
            </div>
        );
    } else {
        content = (
            <div className="myvotes-list">
                {displayedRecords.map((vote) => (
                    <div key={vote.id} className="myvotes-item">
                        <div className="myvotes-item-header">
                            <div>
                                <h3 className="myvotes-election-name">{vote.electionName}</h3>
                                <Link className="myvotes-election-link" to={`/citizen/elections/${vote.electionId}`}>
                                    {t("myVotesPage.actions.openElection")}
                                </Link>
                            </div>
                            <span className={`myvotes-status myvotes-status--${vote.status}`}>
                                {vote.status === "confirmed"
                                    ? t("myVotesPage.status.confirmed")
                                    : t("myVotesPage.status.recorded")}
                            </span>
                        </div>

                        <div className="myvotes-item-details">
                            <div className="myvotes-detail-row">
                                <span className="myvotes-label">{t("myVotesPage.labels.voted")}:</span>
                                <span className="myvotes-value">{formatDate(vote.votedAt, locale, notAvailableOnBrowser)}</span>
                            </div>

                            <div className="myvotes-detail-row">
                                <span className="myvotes-label">{t("myVotesPage.labels.transaction")}:</span>
                                <div className="myvotes-hash-container">
                                    <code className="myvotes-hash" title={vote.transactionHash ?? undefined}>
                                        {truncateHash(vote.transactionHash, notAvailable)}
                                    </code>
                                    {vote.transactionHash ? (
                                        <button
                                            className="myvotes-copy-btn"
                                            onClick={() => void copyToClipboard(transactionHashLabel, vote.transactionHash)}
                                            title={t("myVotesPage.actions.copyTransactionHash")}
                                        >
                                            {t("myVotesPage.actions.copy")}
                                        </button>
                                    ) : null}
                                </div>
                            </div>

                            <div className="myvotes-detail-row">
                                <span className="myvotes-label">{t("myVotesPage.labels.ciphertext")}:</span>
                                <div className="myvotes-hash-container">
                                    <code className="myvotes-hash" title={vote.ciphertextHash ?? undefined}>
                                        {truncateHash(vote.ciphertextHash, notAvailable)}
                                    </code>
                                    {vote.ciphertextHash ? (
                                        <button
                                            className="myvotes-copy-btn"
                                            onClick={() => void copyToClipboard(ciphertextHashLabel, vote.ciphertextHash)}
                                            title={t("myVotesPage.actions.copyCiphertextHash")}
                                        >
                                            {t("myVotesPage.actions.copy")}
                                        </button>
                                    ) : null}
                                </div>
                            </div>

                            <div className="myvotes-detail-row">
                                <span className="myvotes-label">{t("myVotesPage.labels.nullifier")}:</span>
                                <div className="myvotes-hash-container">
                                    <code className="myvotes-hash" title={vote.nullifier ?? undefined}>
                                        {truncateHash(vote.nullifier, notAvailable)}
                                    </code>
                                    {vote.nullifier ? (
                                        <button
                                            className="myvotes-copy-btn"
                                            onClick={() => void copyToClipboard(nullifierLabel, vote.nullifier)}
                                            title={t("myVotesPage.actions.copyNullifier")}
                                        >
                                            {t("myVotesPage.actions.copy")}
                                        </button>
                                    ) : null}
                                </div>
                            </div>

                            <div className="myvotes-detail-row">
                                <span className="myvotes-label">{t("myVotesPage.labels.block")}:</span>
                                <span className="myvotes-value">
                                    {typeof vote.blockNumber === "number" ? vote.blockNumber.toLocaleString(locale) : notAvailable}
                                </span>
                            </div>
                        </div>

                        {vote.receiptAvailable ? null : (
                            <div className="myvotes-note">
                                {t("myVotesPage.messages.receiptLocalOnly")}
                            </div>
                        )}
                    </div>
                ))}
            </div>
        );
    }

    return (
        <div className="dashboard-page">
            <div className="dashboard-card myvotes-card">
                <h2 className="dashboard-title">{t("myVotesPage.title")}</h2>
                <p className="dashboard-subtitle">{subtitle}</p>
                {content}
            </div>
        </div>
    );
};

export default MyVotes;
