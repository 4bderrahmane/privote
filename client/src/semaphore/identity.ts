import {Identity} from "@semaphore-protocol/identity";

const textEncoder = new TextEncoder();
const CURRENT_VAULT_VERSION = 2 as const;
const CURRENT_KDF = "PBKDF2-SHA256" as const;
const CURRENT_CIPHER = "AES-GCM-256" as const;
const CURRENT_PBKDF2_ITERATIONS = 310_000;

// These are absolute bounds for accepting metadata from an untrusted vault.
// They are NOT your policy target.
// They are sanity/DoS bounds.
const MIN_ALLOWED_PBKDF2_ITERATIONS = 210_000;
const MAX_ALLOWED_PBKDF2_ITERATIONS = 2_000_000;

const MASTER_SECRET_BYTES = 32;
const PBKDF2_SALT_BYTES = 16;
const AES_GCM_IV_BYTES = 12;
const MIN_PASSWORD_LENGTH = 10;

export type IdentityVault = {
    version: 2;
    cipher: "AES-GCM-256";
    kdf: {
        name: "PBKDF2-SHA256";
        iterations: number;
        saltB64: string;
    };
    ivB64: string;
    ciphertextB64: string;
};

export type ElectionKey =
    | { kind: "uuid"; value: string }
    | { kind: "address"; value: string }
    | { kind: "externalNullifier"; value: bigint | number | string }
    | { kind: "raw"; value: string };

function normalizeExternalNullifier(value: bigint | number | string): string {
    if (typeof value === "bigint") {
        if (value < 0n) {
            throw new Error("externalNullifier cannot be negative.");
        }
        return value.toString(10);
    }

    if (typeof value === "number") {
        if (!Number.isSafeInteger(value) || value < 0) {
            throw new Error("externalNullifier number must be a non-negative safe integer.");
        }
        return String(value);
    }

    const trimmed = value.trim();
    if (!/^\d+$/.test(trimmed)) {
        throw new Error(
            "externalNullifier string must be a canonical non-negative decimal string."
        );
    }

    return trimmed.replace(/^0+(?=\d)/, "");
}

/**
 * Use this helper in both registration and vote flows to avoid key-domain mismatch.
 */
export function electionKeyFromExternalNullifier(
    value: bigint | number | string
): ElectionKey {
    return { kind: "externalNullifier", value: normalizeExternalNullifier(value) };
}

function assertWebCrypto(): Crypto {
    const c = globalThis.crypto;
    if (!c?.subtle) {
        throw new Error("Web Crypto API is not available in this environment.");
    }
    return c;
}

function assertNonEmptyPassword(password: unknown): void {
    if (typeof password !== "string" || password.length < MIN_PASSWORD_LENGTH) {
        throw new Error(
            `Password must be a string with at least ${MIN_PASSWORD_LENGTH} characters.`
        );
    }
}

function assertIterations(iterations: number): void {
    if (!Number.isInteger(iterations)) {
        throw new TypeError("PBKDF2 iterations must be an integer.");
    }
    if (
        iterations < MIN_ALLOWED_PBKDF2_ITERATIONS ||
        iterations > MAX_ALLOWED_PBKDF2_ITERATIONS
    ) {
        throw new Error(
            `PBKDF2 iterations are out of allowed range: ${iterations}. ` +
            `Allowed range: ${MIN_ALLOWED_PBKDF2_ITERATIONS}..${MAX_ALLOWED_PBKDF2_ITERATIONS}.`
        );
    }
}

function bytesToHex(bytes: Uint8Array): string {
    return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function asBufferSource(bytes: Uint8Array): BufferSource {
    return bytes as unknown as BufferSource;
}

function base64Encode(bytes: Uint8Array): string {
    let binary = "";
    for (const b of bytes) binary += String.fromCodePoint(b);
    return btoa(binary);
}

function base64DecodeStrict(b64: unknown, label: string): Uint8Array {
    if (typeof b64 !== "string" || b64.length === 0) {
        throw new Error(`${label} must be a non-empty base64 string.`);
    }

    let binary: string;
    try {
        binary = atob(b64);
    } catch {
        throw new Error(`${label} is not valid base64.`);
    }

    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        out[i] = binary.charCodeAt(i);
    }

    return out;
}

/**
 * Best-effort zeroization.
 * JS cannot guarantee this because of GC/runtime behavior,
 * but wiping owned Uint8Arrays is still worth doing.
 */
export function wipeBytes(bytes: Uint8Array | undefined | null): void {
    if (!bytes) return;
    bytes.fill(0);
}

function validateVaultShape(vault: IdentityVault): void {
    if (vault.version !== CURRENT_VAULT_VERSION) {
        throw new Error(`Unsupported identity vault version: ${vault.version}`);
    }

    if (vault.cipher !== CURRENT_CIPHER) {
        throw new Error(`Unsupported cipher: ${vault.cipher}`);
    }

    if (vault.kdf?.name !== CURRENT_KDF) {
        throw new Error(`Unsupported KDF: ${vault.kdf?.name}`);
    }

    assertIterations(vault.kdf.iterations);

    const salt = base64DecodeStrict(vault.kdf.saltB64, "saltB64");
    const iv = base64DecodeStrict(vault.ivB64, "ivB64");
    const ciphertext = base64DecodeStrict(vault.ciphertextB64, "ciphertextB64");

    if (salt.length !== PBKDF2_SALT_BYTES) {
        throw new Error(
            `Invalid salt length: ${salt.length}. Expected ${PBKDF2_SALT_BYTES} bytes.`
        );
    }

    if (iv.length !== AES_GCM_IV_BYTES) {
        throw new Error(
            `Invalid IV length: ${iv.length}. Expected ${AES_GCM_IV_BYTES} bytes.`
        );
    }

    // Ciphertext must contain at least the encrypted 32-byte master secret + 16-byte GCM tag.
    if (ciphertext.length < MASTER_SECRET_BYTES + 16) {
        throw new Error(
            `Ciphertext is too short: ${ciphertext.length}. ` +
            `Expected at least ${MASTER_SECRET_BYTES + 16} bytes.`
        );
    }
}

/**
 * Stable metadata AAD used for AES-GCM authentication.
 * If the metadata is tampered with, decryption fails.
 */
function buildVaultAAD(vault: Pick<IdentityVault, "version" | "cipher" | "kdf">): Uint8Array {
    const stable = JSON.stringify({
        version: vault.version,
        cipher: vault.cipher,
        kdf: {
            name: vault.kdf.name,
            iterations: vault.kdf.iterations,
            saltB64: vault.kdf.saltB64,
        },
    });

    return textEncoder.encode(stable);
}

async function deriveAesKeyFromPassword(
    password: string,
    salt: Uint8Array,
    iterations: number
): Promise<CryptoKey> {
    const cryptoApi = assertWebCrypto();

    const keyMaterial = await cryptoApi.subtle.importKey(
        "raw",
        textEncoder.encode(password),
        "PBKDF2",
        false,
        ["deriveKey"]
    );

    return cryptoApi.subtle.deriveKey(
        {
            name: "PBKDF2",
            salt: asBufferSource(salt),
            iterations,
            hash: "SHA-256",
        },
        keyMaterial,
        {
            name: "AES-GCM",
            length: 256,
        },
        false,
        ["encrypt", "decrypt"]
    );
}

function normalizeElectionKey(key: ElectionKey): string {
    switch (key.kind) {
        case "uuid": {
            const normalized = key.value.trim().toLowerCase();
            const uuidPattern =
                /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

            if (!uuidPattern.test(normalized)) {
                throw new Error(`Invalid UUID election key: ${key.value}`);
            }

            return `uuid:${normalized}`;
        }

        case "address": {
            const normalized = key.value.trim().toLowerCase();
            const addressPattern = /^0x[0-9a-f]{40}$/;

            if (!addressPattern.test(normalized)) {
                throw new Error(`Invalid address election key: ${key.value}`);
            }

            return `address:${normalized}`;
        }

        case "externalNullifier": {
            const dec = normalizeExternalNullifier(key.value);
            return `externalNullifier:${dec}`;
        }

        case "raw": {
            const normalized = key.value.trim();
            if (!normalized) {
                throw new Error("Raw election key cannot be empty.");
            }
            return `raw:${normalized}`;
        }
    }
}

async function deriveElectionSeed(
    masterSecret: Uint8Array,
    electionKey: ElectionKey
): Promise<Uint8Array> {
    const cryptoApi = assertWebCrypto();

    const normalized = normalizeElectionKey(electionKey);
    const info = textEncoder.encode(`semaphore-election:${normalized}`);

    const hmacKey = await cryptoApi.subtle.importKey(
        "raw",
        asBufferSource(masterSecret),
        {
            name: "HMAC",
            hash: "SHA-256",
        },
        false,
        ["sign"]
    );

    const mac = await cryptoApi.subtle.sign("HMAC", hmacKey, asBufferSource(info));
    return new Uint8Array(mac); // 32 bytes
}

/**
 * Creates a fresh random master secret and returns an encrypted vault.
 *
 * Store this vault in localStorage / IndexedDB / backend profile storage, etc.
 * The vault is safe to store, but the password remains security-critical.
 */
export async function createIdentityVault(
    password: string,
    iterations = CURRENT_PBKDF2_ITERATIONS
): Promise<IdentityVault> {
    assertNonEmptyPassword(password);
    assertIterations(iterations);

    const cryptoApi = assertWebCrypto();

    const masterSecret = cryptoApi.getRandomValues(
        new Uint8Array(MASTER_SECRET_BYTES)
    );
    const salt = cryptoApi.getRandomValues(new Uint8Array(PBKDF2_SALT_BYTES));
    const iv = cryptoApi.getRandomValues(new Uint8Array(AES_GCM_IV_BYTES));

    const vaultBase = {
        version: CURRENT_VAULT_VERSION,
        cipher: CURRENT_CIPHER,
        kdf: {
            name: CURRENT_KDF,
            iterations,
            saltB64: base64Encode(salt),
        },
    } as const;

    const aad = buildVaultAAD(vaultBase);
    const aesKey = await deriveAesKeyFromPassword(password, salt, iterations);

    try {
        const ciphertext = await cryptoApi.subtle.encrypt(
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(aad),
            },
            aesKey,
            asBufferSource(masterSecret)
        );

        return {
            ...vaultBase,
            ivB64: base64Encode(iv),
            ciphertextB64: base64Encode(new Uint8Array(ciphertext)),
        };
    } finally {
        wipeBytes(masterSecret);
    }
}

/**
 * Decrypts the locally stored master secret from the vault using the user's password.
 *
 * Note:
 * - callers should wipe the returned Uint8Array when done, if they keep it around.
 * - prefer using deriveElectionIdentityFromVault() which wipes internally.
 */
export async function decryptMasterSecret(
    password: string,
    vault: IdentityVault
): Promise<Uint8Array> {
    assertNonEmptyPassword(password);
    validateVaultShape(vault);

    const cryptoApi = assertWebCrypto();

    const salt = base64DecodeStrict(vault.kdf.saltB64, "saltB64");
    const iv = base64DecodeStrict(vault.ivB64, "ivB64");
    const ciphertext = base64DecodeStrict(vault.ciphertextB64, "ciphertextB64");

    const aad = buildVaultAAD({
        version: vault.version,
        cipher: vault.cipher,
        kdf: vault.kdf,
    });

    const aesKey = await deriveAesKeyFromPassword(
        password,
        salt,
        vault.kdf.iterations
    );

    let plaintext: ArrayBuffer;
    try {
        plaintext = await cryptoApi.subtle.decrypt(
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(aad),
            },
            aesKey,
            asBufferSource(ciphertext)
        );
    } catch {
        throw new Error("Failed to decrypt identity vault. Wrong password or tampered/corrupted vault.");
    }

    const masterSecret = new Uint8Array(plaintext);

    if (masterSecret.length !== MASTER_SECRET_BYTES) {
        wipeBytes(masterSecret);
        throw new Error(`Invalid master secret length: ${masterSecret.length}`);
    }

    return masterSecret;
}

/**
 * Returns true if this vault should be re-encrypted with current policy.
 *
 * Example trigger:
 * - iteration count is below current target
 * - future versions/KDFs differ
 */
export function needsVaultUpgrade(vault: IdentityVault): boolean {
    try {
        validateVaultShape(vault);
    } catch {
        return true;
    }

    return (
        vault.version !== CURRENT_VAULT_VERSION ||
        vault.cipher !== CURRENT_CIPHER ||
        vault.kdf.name !== CURRENT_KDF ||
        vault.kdf.iterations < CURRENT_PBKDF2_ITERATIONS
    );
}

/**
 * Rewraps an existing vault using current policy or caller-provided stronger iterations.
 *
 * Use this after a successful unlock if you want seamless migration.
 */
export async function upgradeIdentityVault(
    password: string,
    vault: IdentityVault,
    iterations = CURRENT_PBKDF2_ITERATIONS
): Promise<IdentityVault> {
    assertNonEmptyPassword(password);
    assertIterations(iterations);

    const masterSecret = await decryptMasterSecret(password, vault);

    try {
        const cryptoApi = assertWebCrypto();
        const salt = cryptoApi.getRandomValues(new Uint8Array(PBKDF2_SALT_BYTES));
        const iv = cryptoApi.getRandomValues(new Uint8Array(AES_GCM_IV_BYTES));

        const nextVaultBase = {
            version: CURRENT_VAULT_VERSION,
            cipher: CURRENT_CIPHER,
            kdf: {
                name: CURRENT_KDF,
                iterations,
                saltB64: base64Encode(salt),
            },
        } as const;

        const aad = buildVaultAAD(nextVaultBase);
        const aesKey = await deriveAesKeyFromPassword(password, salt, iterations);

        const ciphertext = await cryptoApi.subtle.encrypt(
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(aad),
            },
            aesKey,
            asBufferSource(masterSecret)
        );

        return {
            ...nextVaultBase,
            ivB64: base64Encode(iv),
            ciphertextB64: base64Encode(new Uint8Array(ciphertext)),
        };
    } finally {
        wipeBytes(masterSecret);
    }
}

/**
 * Derives a Semaphore identity for one specific election from the decrypted master secret.
 *
 * Use the same election key at:
 * - registration time (to compute the commitment to add on-chain)
 * - vote time (to generate the proof)
 */
export async function deriveElectionIdentityFromMasterSecret(
    masterSecret: Uint8Array,
    electionKey: ElectionKey
): Promise<Identity> {
    if (masterSecret.length !== MASTER_SECRET_BYTES) {
        throw new Error(`Invalid master secret length: ${masterSecret.length}`);
    }

    const seedBytes = await deriveElectionSeed(masterSecret, electionKey);

    try {
        const seedHex = bytesToHex(seedBytes);
        return new Identity(seedHex);
    } finally {
        wipeBytes(seedBytes);
    }
}

/**
 * Convenience helper:
 * - decrypt the vault
 * - derive the election-specific identity
 * - wipe the decrypted master secret before returning
 */
export async function deriveElectionIdentityFromVault(
    password: string,
    vault: IdentityVault,
    electionKey: ElectionKey
): Promise<Identity> {
    const masterSecret = await decryptMasterSecret(password, vault);

    try {
        return await deriveElectionIdentityFromMasterSecret(masterSecret, electionKey);
    } finally {
        wipeBytes(masterSecret);
    }
}

/**
 * Convenience helper for first-time enrollment:
 * - creates a fresh vault
 * - immediately derives the election identity
 */
export async function createVaultAndElectionIdentity(
    password: string,
    electionKey: ElectionKey,
    iterations = CURRENT_PBKDF2_ITERATIONS
): Promise<{ vault: IdentityVault; identity: Identity }> {
    const vault = await createIdentityVault(password, iterations);
    const identity = await deriveElectionIdentityFromVault(password, vault, electionKey);

    return {vault, identity};
}
