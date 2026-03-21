import { useState, type ChangeEvent, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { MdBackspace } from "react-icons/md";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { useAuth } from "@/auth/useAuth.ts";
import { createElection, electionManagement } from "../services/ElectionService";
import {
    createElectionKeyVault,
    createStoredElectionKeyVault,
    ELECTION_VAULT_MIN_PASSWORD_LENGTH,
} from "@/crypto/electionKeys";
import {
    downloadElectionKeyVaultBackup,
    saveElectionKeyVault,
} from "@/crypto/electionKeyVaultStorage";
import "../styles/CreateElection.css";

type CreateElectionForm = {
    title: string;
    description: string;
    startDate: string;
    startClock: string;
    endDate: string;
    endClock: string;
    vaultPassword: string;
    confirmVaultPassword: string;
};

const INITIAL_FORM: CreateElectionForm = {
    title: "",
    description: "",
    startDate: "",
    startClock: "09:00",
    endDate: "",
    endClock: "18:00",
    vaultPassword: "",
    confirmVaultPassword: "",
};

function toIsoString(dateValue: string, timeValue: string) {
    if (!dateValue) {
        return "";
    }

    const safeTime = timeValue || "00:00";
    return new Date(`${dateValue}T${safeTime}`).toISOString();
}

export default function CreateElection() {
    const [form, setForm] = useState(INITIAL_FORM);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState("");
    const auth = useAuth();
    const navigate = useNavigate();
    const { showSuccessToast } = useSuccessToast();
    const { t, ready } = useTranslation("elections", { keyPrefix: "create" });

    if (auth.status !== "authenticated" || !ready) {
        return null;
    }

    const handleChange = (
        event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>
    ) => {
        const { name, value } = event.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();
        setError("");

        if (!form.title.trim()) {
            setError(t("errors.titleRequired", { defaultValue: "Title is required." }));
            return;
        }

        if (!form.endDate) {
            setError(t("errors.endTimeRequired", { defaultValue: "End time is required." }));
            return;
        }

        const startTimeIso = form.startDate
            ? toIsoString(form.startDate, form.startClock)
            : "";
        const endTimeIso = toIsoString(form.endDate, form.endClock);

        if (startTimeIso && new Date(endTimeIso) < new Date(startTimeIso)) {
            setError(t("errors.endAfterStart", { defaultValue: "End time must be after start time." }));
            return;
        }

        if (!form.vaultPassword) {
            setError(t("errors.passwordRequired", { defaultValue: "Vault password is required." }));
            return;
        }

        if (form.vaultPassword.length < ELECTION_VAULT_MIN_PASSWORD_LENGTH) {
            setError(t("errors.passwordTooShort", {
                min: ELECTION_VAULT_MIN_PASSWORD_LENGTH,
                defaultValue: `Vault password must be at least ${ELECTION_VAULT_MIN_PASSWORD_LENGTH} characters.`,
            }));
            return;
        }

        if (form.vaultPassword !== form.confirmVaultPassword) {
            setError(t("errors.passwordMismatch", { defaultValue: "Vault password confirmation does not match." }));
            return;
        }

        setSaving(true);
        try {
            const generatedKeys = await createElectionKeyVault(form.vaultPassword);

            const created = await createElection({
                title: form.title,
                description: form.description,
                startTime: startTimeIso || undefined,
                endTime: endTimeIso,
                phase: "REGISTRATION",
                coordinatorKeycloakId: auth.user.id,
                encryptionPublicKey: generatedKeys.publicKeyHex,
            });

            const storedVault = createStoredElectionKeyVault({
                electionPublicId: created.publicId,
                electionTitle: created.title,
                publicKeyHex: generatedKeys.publicKeyHex,
                vault: generatedKeys.vault,
            });

            saveElectionKeyVault(storedVault);
            downloadElectionKeyVaultBackup(storedVault);

            showSuccessToast(
                t("success", {
                    title: created.title,
                    fingerprint: generatedKeys.publicKeyFingerprint,
                    defaultValue: `Election "${created.title}" created.`,
                }),
                5000
            );
            navigate("/admin/elections", { replace: true });
        } catch (submitError) {
            setError(
                electionManagement.asErrorMessage(
                    submitError,
                    t("errors.submitFailed", { defaultValue: "Unable to create election." })
                )
            );
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="create-election-page">
            <div className="create-election-card">
                <div className="create-election-header">
                    <div>
                        <h1 className="create-election-title">{t("title", { defaultValue: "Create election" })}</h1>
                        <p className="create-election-subtitle">
                            {t("subtitle", {
                                defaultValue: "A fresh election encryption keypair is generated in your browser. Only the public key is sent to the backend.",
                            })}
                        </p>
                    </div>

                    <Link to="/admin/elections" className="create-election-back">
                        <MdBackspace className="create-election-back-icon" aria-hidden="true" />
                        <span>{t("back", { defaultValue: "Back to elections" })}</span>
                    </Link>
                </div>

                <form className="create-election-form" onSubmit={handleSubmit}>
                    <div className="create-election-grid">
                        <label className="create-election-field">
                            <span>{t("fields.title", { defaultValue: "Title" })}</span>
                            <input
                                name="title"
                                value={form.title}
                                onChange={handleChange}
                                placeholder={t("placeholders.title", { defaultValue: "Municipal Election 2026" })}
                                required
                            />
                        </label>

                        <label className="create-election-field">
                            <span>{t("fields.phase", { defaultValue: "Phase" })}</span>
                            <input value={t("phases.REGISTRATION", { defaultValue: "Registration" })} disabled />
                        </label>

                        <label className="create-election-field create-election-field-wide">
                            <span>{t("fields.description", { defaultValue: "Description" })}</span>
                            <textarea
                                name="description"
                                value={form.description}
                                onChange={handleChange}
                                placeholder={t("placeholders.description", { defaultValue: "Describe the purpose and scope of this election." })}
                                rows={4}
                            />
                        </label>

                        <label className="create-election-field">
                            <span>{t("fields.startTime", { defaultValue: "Start time" })}</span>
                            <div className="create-election-date-time">
                                <input
                                    name="startDate"
                                    type="date"
                                    value={form.startDate}
                                    onChange={handleChange}
                                    aria-label={t("fields.startDate", { defaultValue: "Start date" })}
                                />
                                <input
                                    name="startClock"
                                    type="time"
                                    step={60}
                                    value={form.startClock}
                                    onChange={handleChange}
                                    aria-label={t("fields.startClock", { defaultValue: "Start hour and minute" })}
                                />
                            </div>
                        </label>

                        <label className="create-election-field">
                            <span>{t("fields.endTime", { defaultValue: "End time" })}</span>
                            <div className="create-election-date-time">
                                <input
                                    name="endDate"
                                    type="date"
                                    value={form.endDate}
                                    onChange={handleChange}
                                    aria-label={t("fields.endDate", { defaultValue: "End date" })}
                                    required
                                />
                                <input
                                    name="endClock"
                                    type="time"
                                    step={60}
                                    value={form.endClock}
                                    onChange={handleChange}
                                    aria-label={t("fields.endClock", { defaultValue: "End hour and minute" })}
                                    required
                                />
                            </div>
                        </label>

                        <label className="create-election-field">
                            <span>{t("fields.coordinatorKeycloakId", { defaultValue: "Coordinator Keycloak ID" })}</span>
                            <input value={auth.user.id} disabled />
                        </label>

                        <label className="create-election-field create-election-field-wide">
                            <span>{t("fields.vaultPassword", { defaultValue: "Vault password" })}</span>
                            <input
                                name="vaultPassword"
                                type="password"
                                autoComplete="new-password"
                                value={form.vaultPassword}
                                onChange={handleChange}
                                minLength={ELECTION_VAULT_MIN_PASSWORD_LENGTH}
                                placeholder={t("placeholders.vaultPassword", {
                                    defaultValue: "Use a long unique password for this election key backup",
                                })}
                                required
                            />
                        </label>

                        <label className="create-election-field create-election-field-wide">
                            <span>{t("fields.confirmVaultPassword", { defaultValue: "Confirm vault password" })}</span>
                            <input
                                name="confirmVaultPassword"
                                type="password"
                                autoComplete="new-password"
                                value={form.confirmVaultPassword}
                                onChange={handleChange}
                                minLength={ELECTION_VAULT_MIN_PASSWORD_LENGTH}
                                placeholder={t("placeholders.confirmVaultPassword", {
                                    defaultValue: "Repeat the same password",
                                })}
                                required
                            />
                        </label>
                    </div>

                    {error ? <div className="create-election-error">{error}</div> : null}

                    <div className="create-election-actions">
                        <button type="submit" className="create-election-submit" disabled={saving}>
                            {saving
                                ? t("submitting", { defaultValue: "Creating..." })
                                : t("submit", { defaultValue: "Create election" })}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
