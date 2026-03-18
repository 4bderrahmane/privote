import React, { useEffect, useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/useAuth.ts";
import { useSuccessToast } from "@/shared/hooks/useSuccessToast.ts";
import {
    deleteAccount,
    getCurrentUser,
    updatePartialProfile,
    userManagement,
} from "../../services/UserManagementService.ts";
import type { SettingsProps, UserResponseDTO, UserUpdateDTO } from "../../types/types.ts";
import "../../styles/settings/Settings.css";
import "../../styles/settings/SettingsForm.css";

type ProfileForm = {
    firstName: string;
    lastName: string;
    email: string;
    phoneNumber: string;
    address: string;
    region: string;
    birthPlace: string;
    birthDate: string;
};

const EMPTY_FORM: ProfileForm = {
    firstName: "",
    lastName: "",
    email: "",
    phoneNumber: "",
    address: "",
    region: "",
    birthPlace: "",
    birthDate: "",
};

const PROFILE_KEYS: Array<keyof ProfileForm> = [
    "firstName",
    "lastName",
    "email",
    "phoneNumber",
    "address",
    "region",
    "birthPlace",
    "birthDate",
];

function toForm(user: UserResponseDTO): ProfileForm {
    return {
        firstName: user.firstName ?? "",
        lastName: user.lastName ?? "",
        email: user.email ?? "",
        phoneNumber: user.phoneNumber ?? "",
        address: user.address ?? "",
        region: user.region ?? "",
        birthPlace: user.birthPlace ?? "",
        birthDate: user.birthDate ?? "",
    };
}

function normalizeForm(form: ProfileForm): ProfileForm {
    return {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        email: form.email.trim(),
        phoneNumber: form.phoneNumber.trim(),
        address: form.address.trim(),
        region: form.region.trim(),
        birthPlace: form.birthPlace.trim(),
        birthDate: form.birthDate.trim(),
    };
}

const Settings: React.FC<SettingsProps> = ({ section }) => {
    const location = useLocation();
    const { t: tSettings } = useTranslation("settings");
    const { t: tCommon } = useTranslation("common");
    const { showSuccessToast } = useSuccessToast();
    const auth = useAuth();

    const sectionFromPath = section ?? (location.pathname.endsWith("/delete") ? "delete" : "profile");

    const [profile, setProfile] = useState<UserResponseDTO | null>(null);
    const [form, setForm] = useState<ProfileForm>(EMPTY_FORM);
    const [initialForm, setInitialForm] = useState<ProfileForm>(EMPTY_FORM);

    const [loadingProfile, setLoadingProfile] = useState(true);
    const [profileError, setProfileError] = useState("");

    const [savingProfile, setSavingProfile] = useState(false);
    const [saveError, setSaveError] = useState("");

    const [confirmDelete, setConfirmDelete] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState("");

    useEffect(() => {
        let isMounted = true;

        async function loadProfile() {
            setLoadingProfile(true);
            setProfileError("");

            try {
                const user = await getCurrentUser();
                if (!isMounted) return;

                const nextForm = toForm(user);
                setProfile(user);
                setForm(nextForm);
                setInitialForm(nextForm);
            } catch (error) {
                if (!isMounted) return;
                setProfileError(userManagement.asErrorMessage(error, tSettings("fetchFailed")));
            } finally {
                if (isMounted) setLoadingProfile(false);
            }
        }

        void loadProfile();

        return () => {
            isMounted = false;
        };
    }, [tSettings]);

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const key = event.target.name as keyof ProfileForm;
        const value = event.target.value;
        setForm((prev) => ({ ...prev, [key]: value }));
    };

    const handleProfileSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        setSaveError("");

        const normalizedForm = normalizeForm(form);
        const normalizedInitial = normalizeForm(initialForm);

        if (!normalizedForm.firstName || !normalizedForm.lastName || !normalizedForm.email) {
            setSaveError(tSettings("requiredFieldsError"));
            return;
        }

        const changedData: Partial<UserUpdateDTO> = {};
        for (const key of PROFILE_KEYS) {
            const nextValue = normalizedForm[key];
            if (nextValue === normalizedInitial[key]) continue;
            if (key === "birthDate" && nextValue.length === 0) continue;
            changedData[key as keyof UserUpdateDTO] = nextValue;
        }

        if (Object.keys(changedData).length === 0) {
            showSuccessToast(tSettings("noChanges"), 2500);
            return;
        }

        setSavingProfile(true);
        try {
            const updatedUser = await updatePartialProfile(changedData);
            const updatedForm = toForm(updatedUser);

            setProfile(updatedUser);
            setForm(updatedForm);
            setInitialForm(updatedForm);
            showSuccessToast(tSettings("profileUpdated"), 3000);
        } catch (error) {
            setSaveError(userManagement.asErrorMessage(error, tSettings("updateFailed")));
        } finally {
            setSavingProfile(false);
        }
    };

    const handleDeleteAccount = async () => {
        setDeleteError("");
        setDeleting(true);

        try {
            await deleteAccount();
            showSuccessToast(tSettings("accountDeleted"), 3000);
            try {
                await auth.logout();
            } catch {
                globalThis.location.assign("/");
            }
        } catch (error) {
            setDeleteError(userManagement.asErrorMessage(error, tSettings("deleteFailed")));
            setDeleting(false);
        }
    };

    return (
        <div className="settings-container">
            <div className="settings-header">
                <h1 className="settings-title">{tSettings("title")}</h1>
                <p className="settings-subtitle">{tSettings("subtitle")}</p>
            </div>

            <nav className="settings-tabs" aria-label={tSettings("title")}>
                <NavLink to="/settings/profile" className={({ isActive }) => (isActive ? "settings-tab active" : "settings-tab")}>
                    {tSettings("editProfile")}
                </NavLink>
                <NavLink
                    to="/settings/delete"
                    className={({ isActive }) =>
                        isActive ? "settings-tab settings-tab-delete active" : "settings-tab settings-tab-delete"
                    }
                >
                    {tSettings("deleteAccount")}
                </NavLink>
            </nav>

            <div className="settings-content">
                {sectionFromPath === "profile" && (
                    <form className="settings-card" onSubmit={handleProfileSubmit}>
                        <h2>{tSettings("editProfile")}</h2>

                        {loadingProfile ? (
                            <div className="settings-status">{tCommon("app.loading")}</div>
                        ) : profileError ? (
                            <div className="settings-error">{profileError}</div>
                        ) : (
                            <div className="form-content">
                                <div className="info-row">
                                    <label className="info-label" htmlFor="username">
                                        {tSettings("username")}
                                    </label>
                                    <input className="info-value" id="username" value={profile?.username ?? ""} disabled />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="cin">
                                        {tSettings("cin")}
                                    </label>
                                    <input className="info-value" id="cin" value={profile?.cin ?? ""} disabled />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="firstName">
                                        {tSettings("firstName")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="text"
                                        id="firstName"
                                        name="firstName"
                                        value={form.firstName}
                                        onChange={handleChange}
                                        autoComplete="given-name"
                                        required
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="lastName">
                                        {tSettings("lastName")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="text"
                                        id="lastName"
                                        name="lastName"
                                        value={form.lastName}
                                        onChange={handleChange}
                                        autoComplete="family-name"
                                        required
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="email">
                                        {tSettings("email")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="email"
                                        id="email"
                                        name="email"
                                        value={form.email}
                                        onChange={handleChange}
                                        autoComplete="email"
                                        required
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="phoneNumber">
                                        {tSettings("phoneNumber")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="tel"
                                        id="phoneNumber"
                                        name="phoneNumber"
                                        value={form.phoneNumber}
                                        onChange={handleChange}
                                        autoComplete="tel"
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="address">
                                        {tSettings("address")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="text"
                                        id="address"
                                        name="address"
                                        value={form.address}
                                        onChange={handleChange}
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="region">
                                        {tSettings("region")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="text"
                                        id="region"
                                        name="region"
                                        value={form.region}
                                        onChange={handleChange}
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="birthPlace">
                                        {tSettings("birthPlace")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="text"
                                        id="birthPlace"
                                        name="birthPlace"
                                        value={form.birthPlace}
                                        onChange={handleChange}
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="birthDate">
                                        {tSettings("birthDate")}
                                    </label>
                                    <input
                                        className="info-value"
                                        type="date"
                                        id="birthDate"
                                        name="birthDate"
                                        value={form.birthDate}
                                        onChange={handleChange}
                                    />
                                </div>
                                <div className="info-row">
                                    <label className="info-label" htmlFor="emailVerified">
                                        {tSettings("emailVerification")}
                                    </label>
                                    <input
                                        className="info-value"
                                        id="emailVerified"
                                        value={
                                            profile?.emailVerified
                                                ? tSettings("verified")
                                                : tSettings("notVerified")
                                        }
                                        disabled
                                    />
                                </div>
                            </div>
                        )}

                        <div className="form-footer">
                            <button type="submit" className="settings-button" disabled={loadingProfile || savingProfile}>
                                {savingProfile ? tCommon("app.loading") : tSettings("saveChanges")}
                            </button>
                        </div>
                        {saveError && <div className="settings-error">{saveError}</div>}
                    </form>
                )}

                {sectionFromPath === "delete" && (
                    <div className="settings-card danger-zone">
                        <h2 className="danger-header">{tSettings("deleteAccount")}</h2>
                        <p>{tSettings("deleteWarning")}</p>

                        {!confirmDelete ? (
                            <div className="form-footer">
                                <button
                                    type="button"
                                    className="settings-button danger-button"
                                    onClick={() => setConfirmDelete(true)}
                                >
                                    {tSettings("continueDelete")}
                                </button>
                            </div>
                        ) : (
                            <div className="form-footer button-group">
                                <button
                                    type="button"
                                    className="settings-button secondary-button"
                                    onClick={() => setConfirmDelete(false)}
                                >
                                    {tSettings("cancel")}
                                </button>
                                <button
                                    type="button"
                                    className="settings-button danger-button"
                                    disabled={deleting}
                                    onClick={handleDeleteAccount}
                                >
                                    {deleting ? tCommon("app.loading") : tSettings("confirmDelete")}
                                </button>
                            </div>
                        )}

                        {deleteError && <div className="settings-error">{deleteError}</div>}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Settings;
