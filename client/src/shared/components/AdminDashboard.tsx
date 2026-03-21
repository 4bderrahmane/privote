import { Link } from "react-router-dom";
import { useAuth } from "@/auth/useAuth.ts";
import { useTranslation } from "react-i18next";
import "../styles/Dashboard.css";

export default function AdminDashboard() {
    const auth = useAuth();
    const { t } = useTranslation("dashboard");

    if (auth.status !== "authenticated") return null;

    const welcomeName = auth.user.name ?? auth.user.username ?? auth.user.email ?? "Admin";

    return (
        <div className="dashboard-page">
            <div className="dashboard-card">
                <h2 className="dashboard-title">{t("admin.title")}</h2>
                <p className="dashboard-subtitle">{t("admin.subtitle", { name: welcomeName })}</p>

                <div className="dashboard-actions">
                    <Link to="/admin/elections/create" className="dashboard-button">
                        {t("admin.action")}
                    </Link>
                </div>
            </div>
        </div>
    );
}
