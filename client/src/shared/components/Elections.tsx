import React, { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { useAuth } from "@/auth/useAuth.ts";
import { isAdminUser } from "@/auth/roles.ts";
import {
  deployElection,
  electionManagement,
  endElection,
  getAllElections,
  startElection,
} from "../services/ElectionService";
import {
  base64ToHex,
  ELECTION_VAULT_MIN_PASSWORD_LENGTH,
  parseStoredElectionKeyVault,
  unlockElectionPrivateKey,
  wipeBytes,
} from "@/crypto/electionKeys";
import {
  loadElectionKeyVault,
  saveElectionKeyVault,
} from "@/crypto/electionKeyVaultStorage";
import "../styles/Elections.css";
import type { Election as ElectionApiModel, ElectionPhase, ElectionStatus } from "../types/election";


type ElectionSummary = {
  id: string;
  title: string;
  description?: string;
  startsAt: string; // ISO
  endsAt: string; // ISO
  phase: ElectionPhase;
  contractAddress?: string | null;
  encryptionPublicKey?: string | null;
  eligibleVoters?: number;
  candidates?: number;
  hasVoted?: boolean;
};

function toSummary(election: ElectionApiModel): ElectionSummary {
  return {
    id: election.publicId,
    title: election.title,
    description: election.description ?? undefined,
    startsAt: election.startTime ?? election.createdAt ?? election.endTime,
    endsAt: election.endTime,
    phase: election.phase,
    contractAddress: election.contractAddress ?? null,
    encryptionPublicKey: election.encryptionPublicKey ?? null,
    hasVoted: false,
  };
}

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
  const { t } = useTranslation("elections");
  const { showSuccessToast } = useSuccessToast();
  const auth = useAuth();
  const isAdmin = auth.status === "authenticated" && isAdminUser(auth.user);
  const navigate = useNavigate();

  const [query, setQuery] = useState("");
  const [elections, setElections] = useState<ElectionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");
  const [actionBusyId, setActionBusyId] = useState<string | null>(null);
  const [endDialogElection, setEndDialogElection] = useState<ElectionSummary | null>(null);
  const [vaultPassword, setVaultPassword] = useState("");
  const [backupJson, setBackupJson] = useState("");
  const [dialogError, setDialogError] = useState("");

  useEffect(() => {
    let cancelled = false;

    async function loadElections() {
      try {
        const data = await getAllElections();
        if (cancelled) return;
        setElections(data.map(toSummary));
      } catch (error) {
        if (cancelled) return;
        setLoadError(electionManagement.asErrorMessage(error, t("list.messages.loadFailed")));
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void loadElections();

    return () => {
      cancelled = true;
    };
  }, [t]);

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
      showSuccessToast(t("list.messages.notOpen"));
      return;
    }

    if (e.hasVoted) {
      showSuccessToast(t("list.messages.alreadyVoted"));
      return;
    }

    void navigate(e.id);
  };

  const handleDeploy = async (election: ElectionSummary) => {
    setActionBusyId(election.id);
    try {
      const updated = await deployElection(election.id);
      setElections((prev) => prev.map((item) => (item.id === election.id ? toSummary(updated) : item)));
      showSuccessToast(t("list.messages.deployed", { title: election.title }));
    } catch (error) {
      const message = electionManagement.asErrorMessage(error, t("list.messages.actionFailed"));
      showSuccessToast(message, 5000, "error");
    } finally {
      setActionBusyId(null);
    }
  };

  const handleStart = async (election: ElectionSummary) => {
    setActionBusyId(election.id);
    try {
      const updated = await startElection(election.id);
      setElections((prev) => prev.map((item) => (item.id === election.id ? toSummary(updated) : item)));
      showSuccessToast(t("list.messages.started", { title: election.title }));
    } catch (error) {
      const message = electionManagement.asErrorMessage(error, t("list.messages.actionFailed"));
      showSuccessToast(message, 5000, "error");
    } finally {
      setActionBusyId(null);
    }
  };

  const openEndDialog = (election: ElectionSummary) => {
    setEndDialogElection(election);
    setVaultPassword("");
    setBackupJson("");
    setDialogError("");
  };

  const closeEndDialog = () => {
    setEndDialogElection(null);
    setVaultPassword("");
    setBackupJson("");
    setDialogError("");
  };

  const handleEndElection = async () => {
    if (!endDialogElection) return;
    if (!vaultPassword) {
      setDialogError(t("list.messages.passwordRequired"));
      return;
    }

    if (vaultPassword.length < ELECTION_VAULT_MIN_PASSWORD_LENGTH) {
      setDialogError(t("list.messages.passwordTooShort", {
        min: ELECTION_VAULT_MIN_PASSWORD_LENGTH,
        defaultValue: `Vault password must be at least ${ELECTION_VAULT_MIN_PASSWORD_LENGTH} characters.`,
      }));
      return;
    }

    setActionBusyId(endDialogElection.id);
    setDialogError("");

    let privateKeyPkcs8: Uint8Array | undefined;
    try {
      const storedRecord = backupJson.trim()
        ? parseStoredElectionKeyVault(backupJson.trim())
        : loadElectionKeyVault(endDialogElection.id);

      if (!storedRecord) {
        throw new Error(t("list.messages.vaultMissing"));
      }

      if (storedRecord.electionPublicId !== endDialogElection.id) {
        throw new Error(t("list.messages.backupInvalid"));
      }

      const expectedPublicKeyHex = endDialogElection.encryptionPublicKey
        ? base64ToHex(endDialogElection.encryptionPublicKey, "encryptionPublicKey")
        : null;

      if (expectedPublicKeyHex && storedRecord.publicKeyHex !== expectedPublicKeyHex) {
        throw new Error(t("list.messages.keyMismatch"));
      }

      privateKeyPkcs8 = await unlockElectionPrivateKey(vaultPassword, storedRecord.vault);
      const updated = await endElection(endDialogElection.id, privateKeyPkcs8);
      saveElectionKeyVault(storedRecord);
      setElections((prev) => prev.map((item) => (item.id === endDialogElection.id ? toSummary(updated) : item)));
      showSuccessToast(t("list.messages.ended", { title: endDialogElection.title }));
      closeEndDialog();
    } catch (error) {
      setDialogError(electionManagement.asErrorMessage(error, t("list.messages.actionFailed")));
    } finally {
      wipeBytes(privateKeyPkcs8);
      setActionBusyId(null);
    }
  };

  return (
    <div className="elections-page">
      <header className="elections-header">
        <div className="elections-lead">
          <h1 className="elections-title">{t("list.title")}</h1>
          <p className="elections-subtitle">{t("list.subtitle")}</p>
        </div>

        <div className="elections-toolbar">
          {isAdmin ? (
            <Link className="primary-button" to="/admin/elections/create">
              {t("list.createButton")}
            </Link>
          ) : null}
          <input
            className="elections-search"
            value={query}
            onChange={(ev) => setQuery(ev.target.value)}
            placeholder={t("list.searchPlaceholder")}
            aria-label={t("list.searchAria")}
          />
        </div>
      </header>

      {loading ? (
        <div className="placeholder-state">
          <div className="placeholder-title">{t("list.loadingTitle")}</div>
        </div>
      ) : loadError ? (
        <div className="placeholder-state">
          <div className="placeholder-title">{t("list.loadErrorTitle")}</div>
          <div className="small-muted">{loadError}</div>
        </div>
      ) : filtered.length === 0 ? (
        <div className="placeholder-state">
          <div className="placeholder-title">{t("list.emptyTitle")}</div>
          <div className="small-muted">{t("list.emptySubtitle")}</div>
        </div>
      ) : (
        <section className="elections-grid" aria-label={t("list.title")}>
          {filtered.map((election) => {
            const start = new Date(election.startsAt).getTime();
            const end = new Date(election.endsAt).getTime();
            const status = computeStatus(now, start, end);
            const canDeploy = !election.contractAddress && end > now;

            let statusLabel = t("list.status.closed");
            if (status === "open") statusLabel = t("list.status.open");
            if (status === "upcoming") statusLabel = t("list.status.upcoming");

            let badgeClass = "badge badge-closed";
            if (status === "open") badgeClass = "badge badge-open";
            if (status === "upcoming") badgeClass = "badge badge-upcoming";

            const canVote = status === "open" && !election.hasVoted;

            let voteButtonLabel = t("list.vote");
            if (status === "open" && election.hasVoted) {
              voteButtonLabel = t("list.voted");
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
                        <span className="stat-label">{t("list.stats.candidates")}:</span>
                        <strong>{election.candidates}</strong>
                      </span>
                    ) : null}
                    {typeof election.eligibleVoters === "number" ? (
                      <span className="stat">
                        <span className="stat-label">{t("list.stats.eligibleVoters")}:</span>
                        <strong>{election.eligibleVoters}</strong>
                      </span>
                    ) : null}
                  </div>

                  <div className="election-actions">
                    <Link className="secondary-button" to={election.id}>
                      {t("list.details")}
                    </Link>
                    {isAdmin && canDeploy ? (
                      <button
                        className="secondary-button"
                        disabled={actionBusyId === election.id}
                        onClick={() => void handleDeploy(election)}
                      >
                        {t("list.deployButton")}
                      </button>
                    ) : null}
                    {isAdmin && election.contractAddress && election.phase === "REGISTRATION" ? (
                      <button
                        className="secondary-button"
                        disabled={actionBusyId === election.id}
                        onClick={() => void handleStart(election)}
                      >
                        {t("list.startButton")}
                      </button>
                    ) : null}
                    {isAdmin && election.contractAddress && election.phase === "VOTING" ? (
                      <button
                        className="secondary-button"
                        disabled={actionBusyId === election.id}
                        onClick={() => openEndDialog(election)}
                      >
                        {actionBusyId === election.id ? t("list.endingButton") : t("list.endButton")}
                      </button>
                    ) : null}
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

      {endDialogElection ? (
        <div className="election-dialog-backdrop">
          <div className="election-dialog" role="dialog" aria-modal="true" aria-labelledby="election-dialog-title">
            <h2 id="election-dialog-title">{t("list.dialog.title")}</h2>
            <p className="small-muted">{t("list.dialog.subtitle")}</p>

            <label className="election-dialog-field">
              <span>{t("list.dialog.password")}</span>
              <input
                type="password"
                value={vaultPassword}
                autoComplete="current-password"
                onChange={(event) => setVaultPassword(event.target.value)}
                minLength={ELECTION_VAULT_MIN_PASSWORD_LENGTH}
              />
            </label>

            <label className="election-dialog-field">
              <span>{t("list.dialog.backupJson")}</span>
              <textarea
                rows={6}
                value={backupJson}
                onChange={(event) => setBackupJson(event.target.value)}
                placeholder={t("list.dialog.backupPlaceholder")}
              />
            </label>

            {dialogError ? <div className="election-dialog-error">{dialogError}</div> : null}

            <div className="election-dialog-actions">
              <button className="secondary-button" onClick={closeEndDialog} disabled={actionBusyId === endDialogElection.id}>
                {t("list.dialog.cancel")}
              </button>
              <button className="primary-button" onClick={() => void handleEndElection()} disabled={actionBusyId === endDialogElection.id}>
                {actionBusyId === endDialogElection.id ? t("list.endingButton") : t("list.dialog.confirm")}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default Elections;
