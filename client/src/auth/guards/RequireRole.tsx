import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../useAuth";
import type { AppRole } from "../roles";
import { getActiveRole } from "../roles";

type RequireRoleProps = {
    allow: AppRole[];
};

export default function RequireRole({ allow }: Readonly<RequireRoleProps>) {
    const auth = useAuth();

    if (auth.status !== "authenticated") {
        return <Navigate to="/" replace />;
    }

    const activeRole = getActiveRole(auth.user);
    return activeRole && allow.includes(activeRole) ? <Outlet /> : <Navigate to="/dashboard" replace />;
}
