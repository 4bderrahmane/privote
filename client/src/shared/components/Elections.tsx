import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSuccessToast } from "../hooks/useSuccessToast";
import "../styles/Elections.css";
import type {ElectionStatus} from "../types";


type ElectionSummary = {
  id: string;
  title: string;
  description?: string;
  startsAt: string; // ISO
  endsAt: string; // ISO
  eligibleVoters?: number;
  candidates?: number;
  hasVoted?: boolean;
};

function computeStatus(now: number, startsAt: number, endsAt: number): ElectionStatus {
  if (now < startsAt) return "upcoming";
  if (now >= startsAt && now <= endsAt) return "open";
  return "closed";
}

function formatRange(startsAtIso: string, endsAtIso: string) {
  const start = new Date(startsAtIso);
  const end = new Date(endsAtIso);
  const fmt: Intl.DateTimeFormatOptions = {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  };

  return `${start.toLocaleString(undefined, fmt)} → ${end.toLocaleString(undefined, fmt)}`;
}

const Elections: React.FC = () => {
  const { t } = useTranslation("common");
  const { showSuccessToast } = useSuccessToast();

  const [query, setQuery] = useState("");

  // Demo data: computed exactly once (avoids react-hooks/purity rule complaints).
  const [elections] = useState<ElectionSummary[]>(() => {
    const base = Date.now();
    return [
      {
        id: "student-council-2026",
        title: "Student Council Election 2026",
        description: "Vote for your representatives.",
        startsAt: new Date(base - 1000 * 60 * 60 * 24).toISOString(),
        endsAt: new Date(base + 1000 * 60 * 60 * 24 * 2).toISOString(),
        eligibleVoters: 328,
        candidates: 6,
        hasVoted: false,
      },
      {
        id: "budget-referendum",
        title: "Budget Referendum",
        description: "Approve / reject the annual budget proposal.",
        startsAt: new Date(base - 1000 * 60 * 60 * 12).toISOString(),
        endsAt: new Date(base + 1000 * 60 * 30).toISOString(),
        eligibleVoters: 120,
        candidates: 2,
        hasVoted: true,
      },
      {
        id: "board-election",
        title: "Board Election",
        description: "Board member selection.",
        startsAt: new Date(base + 1000 * 60 * 60 * 24 * 5).toISOString(),
        endsAt: new Date(base + 1000 * 60 * 60 * 24 * 8).toISOString(),
        eligibleVoters: 980,
        candidates: 12,
        hasVoted: false,
      },
    ];
  });

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return elections;
    return elections.filter((e) => {
      const hay = `${e.title} ${e.description ?? ""}`.toLowerCase();
      return hay.includes(q);
    });
  }, [elections, query]);

  const [now] = useState(() => Date.now());

  const handleVote = (e: ElectionSummary) => {
    const status = computeStatus(now, new Date(e.startsAt).getTime(), new Date(e.endsAt).getTime());

    if (status !== "open") {
      showSuccessToast("This election is not open yet.");
      return;
    }

    if (e.hasVoted) {
      showSuccessToast("You already voted in this election.");
      return;
    }

    // Placeholder: wire this to a real vote flow later.
    showSuccessToast(`Voting started for: ${e.title}`);
  };

  const handleDetails = (e: ElectionSummary) => {
    // Placeholder: wire to /elections/:id later.
    showSuccessToast(`Opening details: ${e.title}`);
  };

  return (
    <div className="elections-page">
      <header className="elections-header">
        <div>
          <h1 className="elections-title">Available elections</h1>
          <p className="elections-subtitle">
            Browse elections you’re eligible for and cast your vote when the election is open.
          </p>
        </div>

        <div className="elections-toolbar">
          <input
            className="elections-search"
            value={query}
            onChange={(ev) => setQuery(ev.target.value)}
            placeholder="Search elections…"
            aria-label="Search elections"
          />
        </div>
      </header>

      {filtered.length === 0 ? (
        <div className="placeholder-state">
          <div className="placeholder-title">No elections found</div>
          <div className="small-muted">Try a different search term.</div>
        </div>
      ) : (
        <section className="elections-grid" aria-label="Elections">
          {filtered.map((election) => {
            const start = new Date(election.startsAt).getTime();
            const end = new Date(election.endsAt).getTime();
            const status = computeStatus(now, start, end);

            let statusLabel = "Closed";
            if (status === "open") statusLabel = "Open";
            if (status === "upcoming") statusLabel = "Upcoming";

            let badgeClass = "badge badge-closed";
            if (status === "open") badgeClass = "badge badge-open";
            if (status === "upcoming") badgeClass = "badge badge-upcoming";

            const canVote = status === "open" && !election.hasVoted;

            let voteButtonLabel = "Vote";
            if (status === "open" && election.hasVoted) {
              voteButtonLabel = "Voted";
            }

            return (
              <article key={election.id} className="election-card">
                <div className="election-card-top">
                  <div>
                    <h2 className="election-name">{election.title}</h2>
                    <p className="election-meta">{formatRange(election.startsAt, election.endsAt)}</p>
                    {election.description ? (
                      <p className="election-meta">{election.description}</p>
                    ) : null}
                  </div>

                  <div className="election-badges">
                    <span className={badgeClass}>{statusLabel}</span>
                  </div>
                </div>

                <div className="election-card-bottom">
                  <div className="election-stats">
                    {typeof election.candidates === "number" ? (
                      <span className="stat">
                        <span className="stat-label">Candidates:</span>
                        <strong>{election.candidates}</strong>
                      </span>
                    ) : null}
                    {typeof election.eligibleVoters === "number" ? (
                      <span className="stat">
                        <span className="stat-label">Eligible voters:</span>
                        <strong>{election.eligibleVoters}</strong>
                      </span>
                    ) : null}
                  </div>

                  <div className="election-actions">
                    <button className="secondary-button" onClick={() => handleDetails(election)}>
                      Details
                    </button>
                    <button
                      className="primary-button"
                      disabled={!canVote}
                      onClick={() => handleVote(election)}
                    >
                      {voteButtonLabel}
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </section>
      )}

      {/* Keep at least one translation call so the namespace isn't tree-shaken in some setups */}
      <span style={{ display: "none" }}>{t("app.loading")}</span>
    </div>
  );
};

export default Elections;

