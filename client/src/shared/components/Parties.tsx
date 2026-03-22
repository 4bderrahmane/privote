import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useSuccessToast } from "../hooks/useSuccessToast";
import { createParty, getAllParties, partyManagement } from "@services/PartyService";
import type { Party } from "../types/party";
import "../styles/Parties.css";

type PartyForm = {
    name: string;
    description: string;
    memberCins: string[];
};

const INITIAL_FORM: PartyForm = {
    name: "",
    description: "",
    memberCins: [""],
};

export default function Parties() {
    const { t } = useTranslation("parties");
    const { showSuccessToast } = useSuccessToast();
    const showErrorToast = useCallback(
        (message: string) => {
            showSuccessToast(message, 5000, "error");
        },
        [showSuccessToast]
    );
    const resolvePartyError = useCallback(
        (error: unknown, fallback: string) => {
            const message = partyManagement.asErrorMessage(error, fallback);
            const missingCitizenMatch = message.match(/citizen not found with cin\s*=\s*(.+)$/i);
            if (missingCitizenMatch) {
                return t("create.errors.memberCinNotFound", {
                    cin: missingCitizenMatch[1].trim(),
                    defaultValue: "Citizen not found with CIN = {{cin}}.",
                });
            }
            if (message === "Unable to reach the backend API. Make sure the server is running.") {
                return t("errors.backendUnavailable", {
                    defaultValue: "Unable to reach the backend API. Make sure the server is running.",
                });
            }
            return message;
        },
        [t]
    );
    const [form, setForm] = useState(INITIAL_FORM);
    const [saving, setSaving] = useState(false);
    const [parties, setParties] = useState<Party[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;

        async function loadParties() {
            setLoading(true);

            try {
                const data = await getAllParties();
                if (cancelled) return;
                setParties(data);
            } catch (error) {
                if (cancelled) return;
                setParties([]);
                showErrorToast(resolvePartyError(error, t("list.loadFailed")));
            } finally {
                if (!cancelled) setLoading(false);
            }
        }

        void loadParties();

        return () => {
            cancelled = true;
        };
    }, [resolvePartyError, showErrorToast, t]);

    const handleChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
        const { name, value } = event.target;
        setForm((prev) => ({ ...prev, [name]: value }));
    };

    const handleMemberCinChange = (index: number, value: string) => {
        setForm((prev) => ({
            ...prev,
            memberCins: prev.memberCins.map((cin, currentIndex) => (currentIndex === index ? value : cin)),
        }));
    };

    const handleAddMemberCin = () => {
        setForm((prev) => ({ ...prev, memberCins: [...prev.memberCins, ""] }));
    };

    const handleRemoveMemberCin = (index: number) => {
        setForm((prev) => {
            if (prev.memberCins.length === 1) {
                return { ...prev, memberCins: [""] };
            }

            return {
                ...prev,
                memberCins: prev.memberCins.filter((_, currentIndex) => currentIndex !== index),
            };
        });
    };

    const handleSubmit = async (event: FormEvent) => {
        event.preventDefault();

        if (!form.name.trim()) {
            showErrorToast(t("create.errors.nameRequired"));
            return;
        }

        const normalizedMemberCins = form.memberCins.map((cin) => cin.trim()).filter((cin) => cin.length > 0);
        if (normalizedMemberCins.length === 0) {
            showErrorToast(t("create.errors.memberCinRequired"));
            return;
        }

        setSaving(true);
        try {
            const created = await createParty({
                name: form.name,
                description: form.description,
                memberCins: normalizedMemberCins,
            });
            setParties((prev) => [created, ...prev]);
            setForm(INITIAL_FORM);
            showSuccessToast(t("create.success", { name: created.name }), 3000);
        } catch (error) {
            showErrorToast(resolvePartyError(error, t("create.errors.submitFailed")));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="parties-page">
            <header className="parties-header">
                <h1 className="parties-title">{t("title")}</h1>
                <p className="parties-subtitle">{t("subtitle")}</p>
            </header>

            <div className="parties-layout">
                <section className="parties-panel">
                    <h2 className="parties-panel-title">{t("create.title")}</h2>
                    <p className="parties-panel-subtitle">{t("create.subtitle")}</p>

                    <form className="parties-form" onSubmit={handleSubmit}>
                        <label className="parties-field">
                            <span>{t("create.fields.name")}</span>
                            <input
                                name="name"
                                value={form.name}
                                onChange={handleChange}
                                placeholder={t("create.placeholders.name")}
                                required
                            />
                        </label>

                        <label className="parties-field">
                            <span>{t("create.fields.description")}</span>
                            <textarea
                                name="description"
                                value={form.description}
                                onChange={handleChange}
                                placeholder={t("create.placeholders.description")}
                                rows={6}
                            />
                        </label>

                        <div className="parties-field">
                            <span>{t("create.fields.memberCin")}</span>

                            <div className="party-member-list">
                                {form.memberCins.map((cin, index) => (
                                    <div key={index} className="party-member-row">
                                        <input
                                            value={cin}
                                            onChange={(event) => handleMemberCinChange(index, event.target.value)}
                                            placeholder={t("create.placeholders.memberCin")}
                                            required={index === 0}
                                        />
                                        <button
                                            type="button"
                                            className="party-member-remove"
                                            onClick={() => handleRemoveMemberCin(index)}
                                        >
                                            {t("create.removeMember")}
                                        </button>
                                    </div>
                                ))}
                            </div>

                            <button type="button" className="party-member-add" onClick={handleAddMemberCin}>
                                {t("create.addMember")}
                            </button>
                        </div>

                        <div className="parties-actions">
                            <button type="submit" className="parties-submit" disabled={saving}>
                                {saving ? t("create.submitting") : t("create.submit")}
                            </button>
                        </div>
                    </form>
                </section>

                <section className="parties-panel">
                    <h2 className="parties-panel-title">{t("list.title")}</h2>

                    {loading ? (
                        <div className="parties-empty">{t("list.loading")}</div>
                    ) : parties.length === 0 ? (
                        <div className="parties-empty">
                            <strong>{t("list.emptyTitle")}</strong>
                            <span>{t("list.emptySubtitle")}</span>
                        </div>
                    ) : (
                        <div className="parties-list">
                            {parties.map((party) => (
                                <article key={party.publicId} className="party-card">
                                    <h3 className="party-name">{party.name}</h3>
                                    <p className="party-description">{party.description || t("list.noDescription")}</p>
                                </article>
                            ))}
                        </div>
                    )}
                </section>
            </div>
        </div>
    );
}
