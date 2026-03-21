import type { IdentityVault } from "./identity";

const STORAGE_KEY = "krino:voter-identity-vault";

function assertStorage(): Storage {
    if (globalThis.window === undefined || !globalThis.localStorage) {
        throw new Error("Persistent browser storage is not available in this environment.");
    }

    return globalThis.localStorage;
}

export function loadIdentityVault(): IdentityVault | null {
    const raw = assertStorage().getItem(STORAGE_KEY);
    if (!raw) {
        return null;
    }

    return JSON.parse(raw) as IdentityVault;
}

export function saveIdentityVault(vault: IdentityVault): void {
    assertStorage().setItem(STORAGE_KEY, JSON.stringify(vault));
}

export function hasIdentityVault(): boolean {
    return loadIdentityVault() !== null;
}

