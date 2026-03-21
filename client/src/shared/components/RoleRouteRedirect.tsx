import { Navigate } from "react-router-dom";
import { useAuth } from "@/auth/useAuth.ts";
import { getActiveRole, getAvailableRoles, getRoleBasePath } from "@/auth/roles.ts";

type RoleRouteRedirectProps = {
    target: "dashboard" | "elections" | "results" | "my-votes";
};

export default function RoleRouteRedirect({ target }: RoleRouteRedirectProps) {
    const auth = useAuth();

    if (auth.status !== "authenticated") return null;

    const availableRoles = getAvailableRoles(auth.user);
    const activeRole = getActiveRole(auth.user);

    if (availableRoles.length > 1 && !activeRole) {
        return <Navigate to="/choose-role" replace />;
    }

    const role = activeRole ?? availableRoles[0];

    if (target === "my-votes" && role === "admin") {
        return <Navigate to="/admin/dashboard" replace />;
    }

    return <Navigate to={`${getRoleBasePath(role)}/${target}`} replace />;
}
