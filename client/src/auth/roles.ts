import type { AuthUser } from "./types";

export type AppRole = "admin" | "citizen";
const ACTIVE_ROLE_STORAGE_KEY = "privote.active-role";

const ADMIN_ROLE_NAMES = new Set([
    "admin",
    "administrator",
    "realm-admin",
    "app-admin",
    "election-admin",
]);

function normalizeRole(role: string) {
    return role.trim().toLowerCase();
}

function collectRoles(user: AuthUser) {
    const resourceRoles = Object.values(user.resourceRoles).flat();
    return [...user.realmRoles, ...resourceRoles].map(normalizeRole);
}

export function isAdminUser(user: AuthUser) {
    return collectRoles(user).some((role) => ADMIN_ROLE_NAMES.has(role));
}

export function getAvailableRoles(user: AuthUser): AppRole[] {
    return isAdminUser(user) ? ["citizen", "admin"] : ["citizen"];
}

function getStorageKey(user: AuthUser) {
    return `${ACTIVE_ROLE_STORAGE_KEY}:${user.id}`;
}

export function getRoleBasePath(role: AppRole) {
    return role === "admin" ? "/admin" : "/citizen";
}

export function getActiveRole(user: AuthUser): AppRole | null {
    const availableRoles = getAvailableRoles(user);
    if (availableRoles.length === 1) return availableRoles[0];

    const storedRole = globalThis.sessionStorage?.getItem(getStorageKey(user));
    if (!storedRole) return null;

    return availableRoles.includes(storedRole as AppRole) ? (storedRole as AppRole) : null;
}

export function setActiveRole(user: AuthUser, role: AppRole) {
    if (!getAvailableRoles(user).includes(role)) return;
    globalThis.sessionStorage?.setItem(getStorageKey(user), role);
}

export function clearActiveRole(user: AuthUser) {
    globalThis.sessionStorage?.removeItem(getStorageKey(user));
}
