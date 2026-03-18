import React from "react";

import "../styles/Dashboard.css";
import "../styles/MyVotes.css";

// Mock data for voting history
interface VoteRecord {
  id: string;
  electionName: string;
  electionId: string;
  votedAt: string;
  proofHash: string;
  status: "confirmed" | "pending";
}

const mockVotes: VoteRecord[] = [
  {
    id: "vote-001",
    electionName: "2025 Presidential Election",
    electionId: "election-2025-pres",
    votedAt: "2025-11-05T14:32:15Z",
    proofHash: "0x8f4a2b3c9d1e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a",
    status: "confirmed",
  },
  {
    id: "vote-002",
    electionName: "City Council Election 2025",
    electionId: "election-2025-council",
    votedAt: "2025-10-15T09:45:30Z",
    proofHash: "0x1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b",
    status: "confirmed",
  },
  {
    id: "vote-003",
    electionName: "School Board Representatives 2025",
    electionId: "election-2025-school",
    votedAt: "2025-09-20T16:20:45Z",
    proofHash: "0x3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d",
    status: "confirmed",
  },
  {
    id: "vote-004",
    electionName: "Community Budget Referendum",
    electionId: "election-2026-budget",
    votedAt: "2026-01-10T11:15:00Z",
    proofHash: "0x5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f",
    status: "pending",
  },
];

const formatDate = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const truncateHash = (hash: string): string => {
  return `${hash.slice(0, 10)}...${hash.slice(-8)}`;
};

const MyVotes: React.FC = () => {
  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      // Could add a toast notification here
    } catch (err) {
      console.error("Failed to copy:", err);
    }
  };

  return (
    <div className="dashboard-page">
      <div className="dashboard-card myvotes-card">
        <h2 className="dashboard-title">My Votes</h2>
        <p className="dashboard-subtitle">
          Your voting history and receipts (proof hashes) are displayed below.
        </p>

        {mockVotes.length === 0 ? (
          <div className="dashboard-status" style={{ paddingTop: 0 }}>
            <div style={{ color: "#6b7280" }}>No votes recorded yet.</div>
          </div>
        ) : (
          <div className="myvotes-list">
            {mockVotes.map((vote) => (
              <div key={vote.id} className="myvotes-item">
                <div className="myvotes-item-header">
                  <h3 className="myvotes-election-name">{vote.electionName}</h3>
                  <span
                    className={`myvotes-status myvotes-status--${vote.status}`}
                  >
                    {vote.status === "confirmed" ? "✓ Confirmed" : "⏳ Pending"}
                  </span>
                </div>
                <div className="myvotes-item-details">
                  <div className="myvotes-detail-row">
                    <span className="myvotes-label">Voted:</span>
                    <span className="myvotes-value">{formatDate(vote.votedAt)}</span>
                  </div>
                  <div className="myvotes-detail-row">
                    <span className="myvotes-label">Proof Hash:</span>
                    <div className="myvotes-hash-container">
                      <code className="myvotes-hash" title={vote.proofHash}>
                        {truncateHash(vote.proofHash)}
                      </code>
                      <button
                        className="myvotes-copy-btn"
                        onClick={() => copyToClipboard(vote.proofHash)}
                        title="Copy full hash"
                      >
                        📋
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default MyVotes;

