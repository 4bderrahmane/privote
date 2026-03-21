import type { CastVoteReceipt } from "../types/vote";

const STORAGE_KEY_PREFIX = "privote.vote-receipts";

export type StoredVoteReceipt = CastVoteReceipt & {
    electionTitle: string;
};

function getStorage(): Storage | null {
    if (typeof globalThis.window === "undefined" || !globalThis.localStorage) {
        return null;
    }

    return globalThis.localStorage;
}

function storageKey(userId: string) {
    return `${STORAGE_KEY_PREFIX}:${userId}`;
}

function isStoredVoteReceipt(value: unknown): value is StoredVoteReceipt {
    if (!value || typeof value !== "object") {
        return false;
    }

    const receipt = value as Partial<StoredVoteReceipt>;
    return (
        typeof receipt.ballotId === "string" &&
        typeof receipt.electionPublicId === "string" &&
        typeof receipt.electionTitle === "string" &&
        typeof receipt.ciphertextHash === "string" &&
        typeof receipt.nullifier === "string" &&
        typeof receipt.transactionHash === "string"
    );
}

function readReceipts(userId: string): StoredVoteReceipt[] {
    const storage = getStorage();
    if (!storage) {
        return [];
    }

    const raw = storage.getItem(storageKey(userId));
    if (!raw) {
        return [];
    }

    try {
        const parsed = JSON.parse(raw);
        if (!Array.isArray(parsed)) {
            return [];
        }

        return parsed.filter(isStoredVoteReceipt);
    } catch {
        return [];
    }
}

function writeReceipts(userId: string, receipts: StoredVoteReceipt[]) {
    const storage = getStorage();
    if (!storage) {
        return;
    }

    storage.setItem(storageKey(userId), JSON.stringify(receipts));
}

export function loadMyVoteReceipts(userId: string): StoredVoteReceipt[] {
    return readReceipts(userId)
        .slice()
        .sort((left, right) => {
            const leftTime = Date.parse(left.castAt ?? "");
            const rightTime = Date.parse(right.castAt ?? "");
            return (Number.isFinite(rightTime) ? rightTime : 0) - (Number.isFinite(leftTime) ? leftTime : 0);
        });
}

export function saveMyVoteReceipt(
    userId: string,
    electionTitle: string,
    receipt: CastVoteReceipt
): StoredVoteReceipt {
    const storedReceipt: StoredVoteReceipt = {
        ...receipt,
        electionTitle,
    };

    const receipts = readReceipts(userId);
    const nextReceipts = [
        storedReceipt,
        ...receipts.filter((entry) => entry.electionPublicId !== receipt.electionPublicId),
    ];

    writeReceipts(userId, nextReceipts);
    return storedReceipt;
}
