import React, {useEffect, useRef} from "react";
import {useTranslation} from "react-i18next";
import {useAuth} from "@/auth/useAuth.ts";
import {useSuccessToast} from "../hooks/useSuccessToast";
import "../styles/Dashboard.css";

const Dashboard: React.FC = () => {
    const {t} = useTranslation("auth");
    const {showSuccessToast} = useSuccessToast();
    const auth = useAuth();

    // Show the “login success” toast only once per mount
    const didToast = useRef(false);

    useEffect(() => {
        if (auth.status === "authenticated" && !didToast.current) {
            didToast.current = true;
            showSuccessToast(t("success.loginSuccess"), 3000);
        }
    }, [auth.status, showSuccessToast, t]);

    if (auth.status === "checking") {
        return (
            <div className="dashboard-page">
                <div className="dashboard-card">
                    <div className="dashboard-status">Loading...</div>
                </div>
            </div>
        );
    }

    // In a properly protected route, you shouldn't hit this often,
    // but it's still safe to handle.
    if (auth.status !== "authenticated") {
        return (
            <div className="dashboard-page">
                <div className="dashboard-card">
                    <div className="dashboard-status">You are not authenticated.</div>
                </div>
            </div>
        );
    }

    const {user} = auth;
    const display = user.email ?? user.username ?? user.id;
    const welcomeName = user.name ?? user.username ?? user.email ?? "Guest";

    const handleLogoutButtonClick = async () => {
        // Note: logout triggers a full redirect, so UI won’t remain long.
        await auth.logout();
    };

    return (
        <div className="dashboard-page">
            <div className="dashboard-card">
                <h2 className="dashboard-title">{t("welcome.welcome", {name: welcomeName})}</h2>

                <p className="dashboard-subtitle">
                    {t("loggedInAs")} <strong>{display}</strong>
                </p>

                <div className="dashboard-actions">
                    <button onClick={handleLogoutButtonClick} className="dashboard-button">
                        {t("logout")}
                    </button>
                </div>
            </div>
        </div>
    );
};

export default Dashboard;
