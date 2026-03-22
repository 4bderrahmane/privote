import { useMemo } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/auth/useAuth.ts";
import { getActiveRole, getAvailableRoles, getRoleBasePath, setActiveRole, type AppRole } from "@/auth/roles.ts";
import "../styles/Dashboard.css";

export default function ChooseRole() {
    const auth = useAuth();
    const navigate = useNavigate();
    const { t } = useTranslation("dashboard");

    const availableRoles = useMemo(
        () => (auth.status === "authenticated" ? getAvailableRoles(auth.user) : []),
        [auth],
    );

    if (auth.status !== "authenticated") {
        return <Navigate to="/" replace />;
    }

    const activeRole = getActiveRole(auth.user);
    if (availableRoles.length === 1) {
        return <Navigate to={`${getRoleBasePath(availableRoles[0])}/dashboard`} replace />;
    }

    if (activeRole) {
        return <Navigate to={`${getRoleBasePath(activeRole)}/dashboard`} replace />;
    }

    const handleChoose = (role: AppRole) => {
        setActiveRole(auth.user, role);
        navigate(`${getRoleBasePath(role)}/dashboard`, { replace: true });
    };

    return (
        <div className="dashboard-page dashboard-page-role">
            <div className="dashboard-card dashboard-card-role">
                <h2 className="dashboard-title">{t("chooseRole.title")}</h2>
                <p className="dashboard-subtitle">{t("chooseRole.subtitle")}</p>

                <div className="role-choice-grid">
                    {availableRoles.map((role) => (
                        <section key={role} className="role-choice-card">
                            <h3 className="role-choice-title">{t(`chooseRole.${role}.title`)}</h3>
                            <p className="role-choice-description">{t(`chooseRole.${role}.description`)}</p>
                            <button
                                type="button"
                                className="dashboard-button role-choice-button"
                                onClick={() => handleChoose(role)}
                            >
                                {t(`chooseRole.${role}.action`)}
                            </button>
                        </section>
                    ))}
                </div>
            </div>
        </div>
    );
}
