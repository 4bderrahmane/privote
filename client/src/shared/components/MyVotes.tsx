import React, { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "@/auth/useAuth.ts";
import { getAllElections, electionManagement } from "../services/ElectionService";
import { loadMyVoteReceipts, type StoredVoteReceipt } from "../services/MyVoteReceiptStorage";
import { getMyRegistrationByElectionPublicId } from "../services/VoterRegistrationService";

import "../styles/Dashboard.css";
import "../styles/MyVotes.css";
import { useSuccessToast } from "../hooks/useSuccessToast";

type VoteRecord = {
    id: string;
    electionId: string;
    electionName: string;
    votedAt?: string | null;
    transactionHash?: string | null;
    ciphertextHash?: string | null;
    nullifier?: string | null;
    blockNumber?: number | null;
    status: "confirmed" | "recorded";
    receiptAvailable: boolean;
};

function formatDate(value?: string | null): string {
    if (!value) {
        return "Not available on this browser";
    }

    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) {
        return "Not available on this browser";
    }

    return parsed.toLocaleDateString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
    });
}

function truncateHash(hash?: string | null): string {
    if (!hash) {
        return "Not available";
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
    const auth = useAuth();
    const { showSuccessToast } = useSuccessToast();
    const userId = auth.status === "authenticated" ? auth.user.id : null;
    const [records, setRecords] = useState<VoteRecord[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState("");

    useEffect(() => {
        if (!userId) {
            setRecords([]);
            setLoadError("");
            setLoading(false);
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
                    if (!registration || registration.participationStatus !== "CAST") {
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
                            "Unable to load your vote history right now."
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
    }, [userId]);

    const subtitle = useMemo(() => {
        if (records.length === 0) {
            return "Confirmed election participations and locally saved ballot receipts will appear here.";
        }

        return "This page shows the elections you have already voted in. Full ballot receipts are available on the browser where the vote was submitted.";
    }, [records.length]);

    const copyToClipboard = async (label: string, text?: string | null) => {
        if (!text) {
            return;
        }

        try {
            await navigator.clipboard.writeText(text);
            showSuccessToast(`${label} copied to clipboard.`);
        } catch (error) {
            console.error("Failed to copy value:", error);
            showSuccessToast(`Unable to copy ${label.toLowerCase()}.`, 3000, "error");
        }
    };

    return (
        <div className="dashboard-page">
            <div className="dashboard-card myvotes-card">
                <h2 className="dashboard-title">My Votes</h2>
                <p className="dashboard-subtitle">{subtitle}</p>

                {loading ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#6b7280" }}>Loading your vote history...</div>
                    </div>
                ) : loadError ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#b42318" }}>{loadError}</div>
                    </div>
                ) : records.length === 0 ? (
                    <div className="dashboard-status" style={{ paddingTop: 0 }}>
                        <div style={{ color: "#6b7280" }}>No vote records were found for this account yet.</div>
                    </div>
                ) : (
                    <div className="myvotes-list">
                        {records.map((vote) => (
                            <div key={vote.id} className="myvotes-item">
                                <div className="myvotes-item-header">
                                    <div>
                                        <h3 className="myvotes-election-name">{vote.electionName}</h3>
                                        <Link className="myvotes-election-link" to={`/citizen/elections/${vote.electionId}`}>
                                            Open election
                                        </Link>
                                    </div>
                                    <span className={`myvotes-status myvotes-status--${vote.status}`}>
                                        {vote.status === "confirmed" ? "Confirmed receipt" : "Recorded participation"}
                                    </span>
                                </div>

                                <div className="myvotes-item-details">
                                    <div className="myvotes-detail-row">
                                        <span className="myvotes-label">Voted:</span>
                                        <span className="myvotes-value">{formatDate(vote.votedAt)}</span>
                                    </div>

                                    <div className="myvotes-detail-row">
                                        <span className="myvotes-label">Transaction:</span>
                                        <div className="myvotes-hash-container">
                                            <code className="myvotes-hash" title={vote.transactionHash ?? undefined}>
                                                {truncateHash(vote.transactionHash)}
                                            </code>
                                            {vote.transactionHash ? (
                                                <button
                                                    className="myvotes-copy-btn"
                                                    onClick={() => void copyToClipboard("Transaction hash", vote.transactionHash)}
                                                    title="Copy full transaction hash"
                                                >
                                                    Copy
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>

                                    <div className="myvotes-detail-row">
                                        <span className="myvotes-label">Ciphertext:</span>
                                        <div className="myvotes-hash-container">
                                            <code className="myvotes-hash" title={vote.ciphertextHash ?? undefined}>
                                                {truncateHash(vote.ciphertextHash)}
                                            </code>
                                            {vote.ciphertextHash ? (
                                                <button
                                                    className="myvotes-copy-btn"
                                                    onClick={() => void copyToClipboard("Ciphertext hash", vote.ciphertextHash)}
                                                    title="Copy full ciphertext hash"
                                                >
                                                    Copy
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>

                                    <div className="myvotes-detail-row">
                                        <span className="myvotes-label">Nullifier:</span>
                                        <div className="myvotes-hash-container">
                                            <code className="myvotes-hash" title={vote.nullifier ?? undefined}>
                                                {truncateHash(vote.nullifier)}
                                            </code>
                                            {vote.nullifier ? (
                                                <button
                                                    className="myvotes-copy-btn"
                                                    onClick={() => void copyToClipboard("Nullifier", vote.nullifier)}
                                                    title="Copy full nullifier"
                                                >
                                                    Copy
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>

                                    <div className="myvotes-detail-row">
                                        <span className="myvotes-label">Block:</span>
                                        <span className="myvotes-value">
                                            {typeof vote.blockNumber === "number" ? vote.blockNumber.toLocaleString("en-US") : "Not available"}
                                        </span>
                                    </div>
                                </div>

                                {!vote.receiptAvailable ? (
                                    <div className="myvotes-note">
                                        A vote was recorded for this election, but the full ballot receipt is only available on the browser where the ballot was submitted.
                                    </div>
                                ) : null}
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default MyVotes;
