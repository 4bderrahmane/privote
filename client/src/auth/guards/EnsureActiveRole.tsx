import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../useAuth";
import { getActiveRole, getAvailableRoles } from "../roles";

export default function EnsureActiveRole() {
    const auth = useAuth();

    if (auth.status !== "authenticated") {
        return <Navigate to="/" replace />;
    }

    const availableRoles = getAvailableRoles(auth.user);
    if (availableRoles.length > 1 && getActiveRole(auth.user) === null) {
        return <Navigate to="/choose-role" replace />;
    }

    return <Outlet />;
}
