import { Navigate } from "react-router-dom";
import { useAuth } from "@/auth/useAuth.ts";
import { getActiveRole, getAvailableRoles, getRoleBasePath } from "@/auth/roles.ts";

export default function DashboardRedirect() {
    const auth = useAuth();

    if (auth.status !== "authenticated") return null;

    const availableRoles = getAvailableRoles(auth.user);
    const activeRole = getActiveRole(auth.user);

    if (availableRoles.length > 1 && !activeRole) {
        return <Navigate to="/choose-role" replace />;
    }

    return <Navigate to={`${getRoleBasePath(activeRole ?? availableRoles[0])}/dashboard`} replace />;
}
