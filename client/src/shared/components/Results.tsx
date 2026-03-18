import React, { useState } from "react";

import "../styles/Dashboard.css";
import "../styles/Results.css";

// Mock data for election results
interface Candidate {
  id: string;
  name: string;
  votes: number;
  percentage: number;
}

interface ElectionResult {
  id: string;
  electionName: string;
  status: "final" | "preliminary";
  endDate: string;
  totalVotes: number;
  totalEligibleVoters: number;
  turnout: number;
  candidates: Candidate[];
}

const mockResults: ElectionResult[] = [
  {
    id: "election-2025-pres",
    electionName: "2025 Presidential Election",
    status: "final",
    endDate: "2025-11-05T23:59:59Z",
    totalVotes: 152847,
    totalEligibleVoters: 200000,
    turnout: 76.4,
    candidates: [
      { id: "c1", name: "Ahmed", votes: 68281, percentage: 44.7 },
      { id: "c2", name: "Riad", votes: 57318, percentage: 37.5 },
      { id: "c3", name: "Salma", votes: 21442, percentage: 14 },
      { id: "c4", name: "Zakaria", votes: 5806, percentage: 3.8 },
    ],
  },
  {
    id: "election-2025-council",
    electionName: "City Council Election 2025",
    status: "final",
    endDate: "2025-10-15T23:59:59Z",
    totalVotes: 45320,
    totalEligibleVoters: 75000,
    turnout: 60.4,
    candidates: [
      { id: "c5", name: "Abderrahmane", votes: 18128, percentage: 40 },
      { id: "c6", name: "Mohcine", votes: 15862, percentage: 35 },
      { id: "c7", name: "Youssef", votes: 11330, percentage: 25 },
    ],
  },
  {
    id: "election-2025-school",
    electionName: "School Board Representatives 2025",
    status: "final",
    endDate: "2025-09-20T23:59:59Z",
    totalVotes: 12450,
    totalEligibleVoters: 25000,
    turnout: 49.8,
    candidates: [
      { id: "c8", name: "Fatima", votes: 5602, percentage: 45 },
      { id: "c9", name: "Khalid", votes: 4356, percentage: 35 },
      { id: "c10", name: "Amina", votes: 2492, percentage: 20 },
    ],
  },
  {
    id: "election-2026-budget",
    electionName: "Community Budget Referendum",
    status: "preliminary",
    endDate: "2026-01-10T23:59:59Z",
    totalVotes: 8750,
    totalEligibleVoters: 30000,
    turnout: 29.2,
    candidates: [
      { id: "c11", name: "Yes - Approve Budget", votes: 5250, percentage: 60 },
      { id: "c12", name: "No - Reject Budget", votes: 3500, percentage: 40 },
    ],
  },
];

const formatDate = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
};

const formatNumber = (num: number): string => {
  return num.toLocaleString("en-US");
};

const Results: React.FC = () => {
  const [expandedElection, setExpandedElection] = useState<string | null>(
    mockResults[0]?.id || null
  );

  const toggleExpand = (electionId: string) => {
    setExpandedElection(expandedElection === electionId ? null : electionId);
  };

  return (
    <div className="dashboard-page">
      <div className="dashboard-card results-card">
        <h2 className="dashboard-title">Results</h2>
        <p className="dashboard-subtitle">
          Published election results are displayed below.
        </p>

        {mockResults.length === 0 ? (
          <div className="dashboard-status" style={{ paddingTop: 0 }}>
            <div style={{ color: "#6b7280" }}>No results available yet.</div>
          </div>
        ) : (
          <div className="results-list">
            {mockResults.map((election) => (
              <div key={election.id} className="results-item">
                <button
                  className="results-item-header"
                  onClick={() => toggleExpand(election.id)}
                  aria-expanded={expandedElection === election.id}
                >
                  <div className="results-header-left">
                    <h3 className="results-election-name">
                      {election.electionName}
                    </h3>
                    <span className="results-date">
                      Ended: {formatDate(election.endDate)}
                    </span>
                  </div>
                  <div className="results-header-right">
                    <span
                      className={`results-status results-status--${election.status}`}
                    >
                      {election.status === "final" ? "Final" : "Preliminary"}
                    </span>
                    <span className="results-expand-icon">
                      {expandedElection === election.id ? "▼" : "▶"}
                    </span>
                  </div>
                </button>

                {expandedElection === election.id && (
                  <div className="results-details">
                    <div className="results-stats">
                      <div className="results-stat">
                        <span className="results-stat-label">Total Votes</span>
                        <span className="results-stat-value">
                          {formatNumber(election.totalVotes)}
                        </span>
                      </div>
                      <div className="results-stat">
                        <span className="results-stat-label">Turnout</span>
                        <span className="results-stat-value">
                          {election.turnout}%
                        </span>
                      </div>
                      <div className="results-stat">
                        <span className="results-stat-label">Eligible Voters</span>
                        <span className="results-stat-value">
                          {formatNumber(election.totalEligibleVoters)}
                        </span>
                      </div>
                    </div>

                    <div className="results-candidates">
                      {election.candidates.map((candidate, index) => (
                        <div
                          key={candidate.id}
                          className={`results-candidate ${index === 0 ? "results-candidate--winner" : ""}`}
                        >
                          <div className="results-candidate-info">
                            <span className="results-candidate-rank">
                              {index === 0 ? "🏆" : `#${index + 1}`}
                            </span>
                            <span className="results-candidate-name">
                              {candidate.name}
                            </span>
                          </div>
                          <div className="results-candidate-votes">
                            <div className="results-progress-bar">
                              <div
                                className="results-progress-fill"
                                style={{ width: `${candidate.percentage}%` }}
                              />
                            </div>
                            <span className="results-votes-count">
                              {formatNumber(candidate.votes)} ({candidate.percentage}%)
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default Results;
