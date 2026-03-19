import {
    parseStoredElectionKeyVault,
    serializeStoredElectionKeyVault,
    type StoredElectionKeyVault,
} from "./electionKeys.ts";

const STORAGE_PREFIX = "krino:election-key-vault:";

function storageKey(electionPublicId: string): string {
    return `${STORAGE_PREFIX}${electionPublicId}`;
}

function assertStorage(): Storage {
    if (globalThis.window === undefined || !globalThis.localStorage) {
        throw new Error("Persistent browser storage is not available in this environment.");
    }

    return globalThis.localStorage;
}

export function saveElectionKeyVault(record: StoredElectionKeyVault): void {
    assertStorage().setItem(storageKey(record.electionPublicId), serializeStoredElectionKeyVault(record));
}

export function loadElectionKeyVault(electionPublicId: string): StoredElectionKeyVault | null {
    const raw = assertStorage().getItem(storageKey(electionPublicId));
    if (!raw) {
        return null;
    }
    return parseStoredElectionKeyVault(raw);
}

export function deleteElectionKeyVault(electionPublicId: string): void {
    assertStorage().removeItem(storageKey(electionPublicId));
}

export function downloadElectionKeyVaultBackup(record: StoredElectionKeyVault): void {
    if (globalThis.window === undefined || typeof document === "undefined") {
        throw new Error("Backup downloads are only available in the browser.");
    }

    const blob = new Blob([serializeStoredElectionKeyVault(record)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `privote-election-key-${record.electionPublicId}.json`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
}
