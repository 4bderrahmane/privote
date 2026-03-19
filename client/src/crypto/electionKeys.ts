import { argon2id } from "@noble/hashes/argon2";

const textEncoder = new TextEncoder();

const LEGACY_VAULT_VERSION = 1 as const;
const CURRENT_VAULT_VERSION = 2 as const;
const CURRENT_VAULT_CIPHER = "AES-GCM-256" as const;
const LEGACY_VAULT_KDF = "PBKDF2-SHA256" as const;
const CURRENT_VAULT_KDF = "ARGON2ID" as const;
const CURRENT_VAULT_ALGORITHM = "X25519-PKCS8" as const;
const CURRENT_ARGON2_ITERATIONS = 3;
const CURRENT_ARGON2_MEMORY_KIB = 64 * 1024;
const CURRENT_ARGON2_PARALLELISM = 1;

export const ELECTION_VAULT_MIN_PASSWORD_LENGTH = 16;
const MIN_ALLOWED_PBKDF2_ITERATIONS = 250_000;
const MAX_ALLOWED_PBKDF2_ITERATIONS = 2_000_000;
const MIN_ALLOWED_ARGON2_ITERATIONS = 1;
const MAX_ALLOWED_ARGON2_ITERATIONS = 10;
const MIN_ALLOWED_ARGON2_MEMORY_KIB = 8 * 1024;
const MAX_ALLOWED_ARGON2_MEMORY_KIB = 1024 * 1024;
const MIN_ALLOWED_ARGON2_PARALLELISM = 1;
const MAX_ALLOWED_ARGON2_PARALLELISM = 8;

const PBKDF2_SALT_BYTES = 16;
const AES_GCM_IV_BYTES = 12;
const X25519_PUBLIC_KEY_BYTES = 32;
const SHARED_SECRET_BYTES = 32;
const MIN_PRIVATE_KEY_CIPHERTEXT_BYTES = 32;
const AES_GCM_TAG_BYTES = 16;

const PAYLOAD_VERSION_LEGACY = 1;
const PAYLOAD_VERSION_CONTEXT_BOUND = 2;
const PAYLOAD_SALT_BYTES = 16;
const PAYLOAD_CONTEXT_HASH_BYTES = 32;
const PAYLOAD_INFO_V1 = textEncoder.encode("privote-election-payload-v1");
const PAYLOAD_INFO_V2_PREFIX = textEncoder.encode("privote-election-payload-v2");
const DEFAULT_PAYLOAD_PROTOCOL_DOMAIN = "privote:election-ballot";
const PAYLOAD_CONTEXT_DOMAIN_MAX_LENGTH = 96;
const PAYLOAD_CONTEXT_ELECTION_ID_MAX_LENGTH = 96;
const PAYLOAD_CONTEXT_FINGERPRINT_MAX_LENGTH = 128;

const VAULT_AAD_V2_LABEL = "privote-election-vault-v2";
const PAYLOAD_CONTEXT_LABEL = "privote-election-payload-context-v2";

const BASE64_CANONICAL_PATTERN = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;

const PAYLOAD_HEADER_V1_BYTES = 1 + X25519_PUBLIC_KEY_BYTES + PAYLOAD_SALT_BYTES + AES_GCM_IV_BYTES;
const PAYLOAD_HEADER_V2_BYTES = PAYLOAD_HEADER_V1_BYTES + PAYLOAD_CONTEXT_HASH_BYTES;

export type VaultSecretMode = "PASSWORD_ONLY" | "PASSWORD_PLUS_CUSTODY_SECRET";

export type VaultCustodyOptions = {
    // Prefer Uint8Array for custodySecret when possible, so temporary copies can be wiped.
    // JS strings are immutable and cannot be zeroized.
    custodySecret?: string | Uint8Array;
};

export type ElectionPayloadContext = {
    electionId: string;
    publicKeyFingerprint: string;
    protocolDomain?: string;
};

type NormalizedElectionPayloadContext = {
    electionId: string;
    publicKeyFingerprint: string;
    protocolDomain: string;
};

type ElectionVaultKdf =
    | {
        name: "PBKDF2-SHA256";
        iterations: number;
        saltB64: string;
        secretMode?: VaultSecretMode;
    }
    | {
        name: "ARGON2ID";
        iterations: number;
        memoryKiB: number;
        parallelism: number;
        saltB64: string;
        secretMode?: VaultSecretMode;
    };

export type ElectionKeyVault = {
    version: typeof LEGACY_VAULT_VERSION | typeof CURRENT_VAULT_VERSION;
    privateKeyAlgorithm: "X25519-PKCS8";
    publicKeyRawB64: string;
    wrapping: {
        cipher: "AES-GCM-256";
        kdf: ElectionVaultKdf;
        ivB64: string;
        ciphertextB64: string;
    };
};

export type StoredElectionKeyVault = {
    electionPublicId: string;
    electionTitle?: string;
    createdAt: string;
    publicKeyHex: string;
    vault: ElectionKeyVault;
};

function assertWebCrypto(): Crypto {
    const cryptoApi = globalThis.crypto;
    if (!cryptoApi?.subtle) {
        throw new Error("Web Crypto API is not available in this environment.");
    }
    return cryptoApi;
}

function assertPassword(password: string): void {
    if (typeof password !== "string" || password.length < ELECTION_VAULT_MIN_PASSWORD_LENGTH) {
        throw new Error(
            `Password must be at least ${ELECTION_VAULT_MIN_PASSWORD_LENGTH} characters long. ` +
            "Use a long unique passphrase for election key custody."
        );
    }
}

function assertPbkdf2Iterations(iterations: number): void {
    if (!Number.isInteger(iterations)) {
        throw new TypeError("PBKDF2 iterations must be an integer.");
    }
    if (iterations < MIN_ALLOWED_PBKDF2_ITERATIONS || iterations > MAX_ALLOWED_PBKDF2_ITERATIONS) {
        throw new Error(
            `PBKDF2 iterations are out of allowed range: ${iterations}. ` +
            `Allowed range: ${MIN_ALLOWED_PBKDF2_ITERATIONS}..${MAX_ALLOWED_PBKDF2_ITERATIONS}.`
        );
    }
}

function assertArgon2Iterations(iterations: number): void {
    if (!Number.isInteger(iterations)) {
        throw new TypeError("Argon2 iterations must be an integer.");
    }
    if (iterations < MIN_ALLOWED_ARGON2_ITERATIONS || iterations > MAX_ALLOWED_ARGON2_ITERATIONS) {
        throw new Error(
            `Argon2 iterations are out of allowed range: ${iterations}. ` +
            `Allowed range: ${MIN_ALLOWED_ARGON2_ITERATIONS}..${MAX_ALLOWED_ARGON2_ITERATIONS}.`
        );
    }
}

function assertArgon2MemoryKiB(memoryKiB: number): void {
    if (!Number.isInteger(memoryKiB)) {
        throw new TypeError("Argon2 memoryKiB must be an integer.");
    }
    if (memoryKiB < MIN_ALLOWED_ARGON2_MEMORY_KIB || memoryKiB > MAX_ALLOWED_ARGON2_MEMORY_KIB) {
        throw new Error(
            `Argon2 memoryKiB is out of allowed range: ${memoryKiB}. ` +
            `Allowed range: ${MIN_ALLOWED_ARGON2_MEMORY_KIB}..${MAX_ALLOWED_ARGON2_MEMORY_KIB}.`
        );
    }
}

function assertArgon2Parallelism(parallelism: number): void {
    if (!Number.isInteger(parallelism)) {
        throw new TypeError("Argon2 parallelism must be an integer.");
    }
    if (parallelism < MIN_ALLOWED_ARGON2_PARALLELISM || parallelism > MAX_ALLOWED_ARGON2_PARALLELISM) {
        throw new Error(
            `Argon2 parallelism is out of allowed range: ${parallelism}. ` +
            `Allowed range: ${MIN_ALLOWED_ARGON2_PARALLELISM}..${MAX_ALLOWED_ARGON2_PARALLELISM}.`
        );
    }
}

function assertVaultKdf(kdf: ElectionVaultKdf): void {
    if (kdf.name === LEGACY_VAULT_KDF) {
        assertPbkdf2Iterations(kdf.iterations);
        return;
    }
    if (kdf.name === CURRENT_VAULT_KDF) {
        assertArgon2Iterations(kdf.iterations);
        assertArgon2MemoryKiB(kdf.memoryKiB);
        assertArgon2Parallelism(kdf.parallelism);
        return;
    }
    throw new Error(`Unsupported vault KDF: ${(kdf as { name?: unknown }).name}`);
}

function asBufferSource(bytes: Uint8Array): BufferSource {
    return bytes as unknown as BufferSource;
}

function bytesToHex(bytes: Uint8Array): string {
    return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
}

function hexToBytes(hex: string): Uint8Array {
    const normalized = hex.trim().replace(/^0x/i, "").replace(/\s+/g, "");
    if (!/^[0-9a-fA-F]+$/.test(normalized)) {
        throw new Error("Expected a hexadecimal string.");
    }
    if (normalized.length % 2 !== 0) {
        throw new Error("Hexadecimal strings must contain an even number of characters.");
    }

    const out = new Uint8Array(normalized.length / 2);
    for (let index = 0; index < out.length; index += 1) {
        out[index] = Number.parseInt(normalized.slice(index * 2, index * 2 + 2), 16);
    }
    return out;
}

function base64Encode(bytes: Uint8Array): string {
    if (typeof globalThis.btoa === "function") {
        let binary = "";
        for (const value of bytes) binary += String.fromCodePoint(value);
        return globalThis.btoa(binary);
    }

    return Buffer.from(bytes).toString("base64");
}

function base64DecodeStrict(value: string, label: string): Uint8Array {
    if (typeof value !== "string" || value.length === 0) {
        throw new Error(`${label} must be a non-empty base64 string.`);
    }
    if (!BASE64_CANONICAL_PATTERN.test(value)) {
        throw new Error(`${label} is not canonical base64.`);
    }

    let out: Uint8Array;
    if (typeof globalThis.atob === "function") {
        let binary: string;
        try {
            binary = globalThis.atob(value);
        } catch {
            throw new Error(`${label} is not valid base64.`);
        }

        out = new Uint8Array(binary.length);
        for (let index = 0; index < binary.length; index += 1) {
            out[index] = binary.charCodeAt(index);
        }
    } else {
        try {
            out = new Uint8Array(Buffer.from(value, "base64"));
        } catch {
            throw new Error(`${label} is not valid base64.`);
        }
    }

    if (base64Encode(out) !== value) {
        throw new Error(`${label} is not canonical base64.`);
    }

    return out;
}

export function base64ToHex(value: string, label = "value"): string {
    return bytesToHex(base64DecodeStrict(value, label));
}

function normalizeUuid(value: string, label: string): string {
    const normalized = value.trim().toLowerCase();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(normalized)) {
        throw new Error(`${label} must be a valid UUID.`);
    }
    return normalized;
}

function buildStructuredAad(label: string, fields: Array<readonly [string, string]>): Uint8Array {
    const parts = [label];
    for (const [key, value] of fields) {
        parts.push(`${key.length}:${key}:${value.length}:${value}`);
    }
    return textEncoder.encode(parts.join("|"));
}

function resolveVaultSecretMode(vault: ElectionKeyVault): VaultSecretMode {
    if (vault.version === LEGACY_VAULT_VERSION) {
        return "PASSWORD_ONLY";
    }

    const mode = vault.wrapping.kdf.secretMode;
    if (mode !== "PASSWORD_ONLY" && mode !== "PASSWORD_PLUS_CUSTODY_SECRET") {
        throw new Error("Unsupported or missing vault secret mode.");
    }
    return mode;
}

function buildVaultAad(vault: ElectionKeyVault): Uint8Array {
    if (vault.version === LEGACY_VAULT_VERSION) {
        return textEncoder.encode(JSON.stringify({
            version: vault.version,
            privateKeyAlgorithm: vault.privateKeyAlgorithm,
            publicKeyRawB64: vault.publicKeyRawB64,
            wrapping: {
                cipher: vault.wrapping.cipher,
                kdf: {
                    name: vault.wrapping.kdf.name,
                    iterations: vault.wrapping.kdf.iterations,
                    saltB64: vault.wrapping.kdf.saltB64,
                },
            },
        }));
    }

    const secretMode = resolveVaultSecretMode(vault);
    const aadFields: Array<readonly [string, string]> = [
        ["version", String(vault.version)],
        ["privateKeyAlgorithm", vault.privateKeyAlgorithm],
        ["publicKeyRawB64", vault.publicKeyRawB64],
        ["cipher", vault.wrapping.cipher],
        ["kdfName", vault.wrapping.kdf.name],
        ["kdfIterations", String(vault.wrapping.kdf.iterations)],
        ["kdfSaltB64", vault.wrapping.kdf.saltB64],
        ["secretMode", secretMode],
    ];

    if (vault.wrapping.kdf.name === CURRENT_VAULT_KDF) {
        aadFields.push(["kdfMemoryKiB", String(vault.wrapping.kdf.memoryKiB)]);
        aadFields.push(["kdfParallelism", String(vault.wrapping.kdf.parallelism)]);
    }

    return buildStructuredAad(VAULT_AAD_V2_LABEL, aadFields);
}

function normalizeCustodySecret(secret: string | Uint8Array | undefined): Uint8Array {
    if (secret === undefined) {
        throw new Error("Vault requires custodySecret in addition to password.");
    }

    if (typeof secret === "string") {
        if (secret.length === 0) {
            throw new Error("custodySecret must not be empty.");
        }
        return textEncoder.encode(secret);
    }

    if (!(secret instanceof Uint8Array)) {
        throw new Error("custodySecret must be a string or Uint8Array.");
    }
    if (secret.length === 0) {
        throw new Error("custodySecret must not be empty.");
    }
    return new Uint8Array(secret);
}

async function deriveWrappingKey(
    password: string,
    salt: Uint8Array,
    kdf: ElectionVaultKdf,
    secretMode: VaultSecretMode,
    options: VaultCustodyOptions
): Promise<CryptoKey> {
    const cryptoApi = assertWebCrypto();
    assertVaultKdf(kdf);

    // Password strings themselves remain in JS-managed immutable memory.
    // We wipe only this transient UTF-8 copy.
    const passwordBytes = textEncoder.encode(password);
    let baseKeyBytes: Uint8Array | undefined;
    let mixedKeyBytes: Uint8Array | undefined;
    let custodySecretBytes: Uint8Array | undefined;

    try {
        if (kdf.name === LEGACY_VAULT_KDF) {
            const keyMaterial = await cryptoApi.subtle.importKey(
                "raw",
                passwordBytes,
                "PBKDF2",
                false,
                ["deriveBits"]
            );
            baseKeyBytes = new Uint8Array(
                await cryptoApi.subtle.deriveBits(
                    {
                        name: "PBKDF2",
                        hash: "SHA-256",
                        salt: asBufferSource(salt),
                        iterations: kdf.iterations,
                    },
                    keyMaterial,
                    256
                )
            );
        } else {
            // Noble Argon2id is pure JS: good for memory hardness in-browser, but not equivalent
            // to native/high-assurance Argon2 deployments.
            baseKeyBytes = argon2id(passwordBytes, salt, {
                t: kdf.iterations,
                m: kdf.memoryKiB,
                p: kdf.parallelism,
                dkLen: 32,
            });
        }

        if (secretMode === "PASSWORD_PLUS_CUSTODY_SECRET") {
            custodySecretBytes = normalizeCustodySecret(options.custodySecret);
            const hmacKey = await cryptoApi.subtle.importKey(
                "raw",
                asBufferSource(baseKeyBytes),
                { name: "HMAC", hash: "SHA-256" },
                false,
                ["sign"]
            );
            mixedKeyBytes = new Uint8Array(
                await cryptoApi.subtle.sign("HMAC", hmacKey, asBufferSource(custodySecretBytes))
            );
        } else {
            mixedKeyBytes = baseKeyBytes;
        }

        return await cryptoApi.subtle.importKey(
            "raw",
            asBufferSource(mixedKeyBytes),
            { name: "AES-GCM" },
            false,
            ["encrypt", "decrypt", "wrapKey", "unwrapKey"]
        );
    } finally {
        wipeBytes(custodySecretBytes);
        wipeBytes(baseKeyBytes);
        if (mixedKeyBytes && mixedKeyBytes !== baseKeyBytes) {
            wipeBytes(mixedKeyBytes);
        }
        wipeBytes(passwordBytes);
    }
}

function validateVault(vault: ElectionKeyVault): void {
    if (vault.version !== LEGACY_VAULT_VERSION && vault.version !== CURRENT_VAULT_VERSION) {
        throw new Error(`Unsupported election key vault version: ${vault.version}`);
    }
    if (vault.privateKeyAlgorithm !== CURRENT_VAULT_ALGORITHM) {
        throw new Error(`Unsupported private key algorithm: ${vault.privateKeyAlgorithm}`);
    }
    if (vault.wrapping.cipher !== CURRENT_VAULT_CIPHER) {
        throw new Error(`Unsupported vault cipher: ${vault.wrapping.cipher}`);
    }
    if (vault.version === LEGACY_VAULT_VERSION && vault.wrapping.kdf.name !== LEGACY_VAULT_KDF) {
        throw new Error("Vault version 1 only supports PBKDF2-SHA256.");
    }

    assertVaultKdf(vault.wrapping.kdf);
    if (vault.version === CURRENT_VAULT_VERSION) {
        resolveVaultSecretMode(vault);
    }

    const publicKey = base64DecodeStrict(vault.publicKeyRawB64, "publicKeyRawB64");
    if (publicKey.length !== X25519_PUBLIC_KEY_BYTES) {
        throw new Error(`Invalid public key length: ${publicKey.length}`);
    }

    const salt = base64DecodeStrict(vault.wrapping.kdf.saltB64, "saltB64");
    const iv = base64DecodeStrict(vault.wrapping.ivB64, "ivB64");
    const ciphertext = base64DecodeStrict(vault.wrapping.ciphertextB64, "ciphertextB64");

    if (salt.length !== PBKDF2_SALT_BYTES) {
        throw new Error(`Invalid salt length: ${salt.length}`);
    }
    if (iv.length !== AES_GCM_IV_BYTES) {
        throw new Error(`Invalid IV length: ${iv.length}`);
    }
    if (ciphertext.length < MIN_PRIVATE_KEY_CIPHERTEXT_BYTES) {
        throw new Error("Encrypted private key blob is too short.");
    }
}

export function getVaultPublicKeyHex(vault: ElectionKeyVault): string {
    validateVault(vault);
    return bytesToHex(base64DecodeStrict(vault.publicKeyRawB64, "publicKeyRawB64"));
}

export function wipeBytes(bytes: Uint8Array | null | undefined): void {
    if (bytes) {
        bytes.fill(0);
    }
}

function assertCryptoKeyPair(key: CryptoKeyPair | CryptoKey): CryptoKeyPair {
    if ("publicKey" in key && "privateKey" in key) {
        return key;
    }

    throw new Error("Expected a CryptoKeyPair.");
}

function assertPrivateX25519Key(key: CryptoKey): void {
    if (key.type !== "private") {
        throw new Error("Expected a private X25519 CryptoKey.");
    }
    if (key.algorithm.name !== "X25519") {
        throw new Error(`Expected X25519 private key, got ${key.algorithm.name}.`);
    }
    if (!key.usages.includes("deriveBits")) {
        throw new Error("Private key must allow deriveBits usage.");
    }
}

function assertSharedSecretIsNonZero(sharedSecret: Uint8Array): void {
    let aggregate = 0;
    for (const value of sharedSecret) {
        aggregate |= value;
    }
    if (aggregate === 0) {
        throw new Error("Invalid X25519 shared secret (all-zero output).");
    }
}

function concatBytes(...chunks: Uint8Array[]): Uint8Array {
    const out = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0));
    let offset = 0;
    for (const chunk of chunks) {
        out.set(chunk, offset);
        offset += chunk.length;
    }
    return out;
}

function normalizePayloadContextValue(
    value: string | undefined,
    label: string,
    maxLength: number,
    required: boolean
): string {
    const normalized = value?.trim() ?? "";
    if (required && normalized.length === 0) {
        throw new Error(`${label} must be provided.`);
    }
    if (normalized.length === 0) {
        return "";
    }
    if (normalized.length > maxLength) {
        throw new Error(`${label} is too long.`);
    }
    return normalized;
}

function normalizePayloadContext(context: ElectionPayloadContext): NormalizedElectionPayloadContext {
    const electionId = normalizePayloadContextValue(
        context.electionId,
        "electionId",
        PAYLOAD_CONTEXT_ELECTION_ID_MAX_LENGTH,
        true
    );
    const publicKeyFingerprint = normalizePayloadContextValue(
        context.publicKeyFingerprint,
        "publicKeyFingerprint",
        PAYLOAD_CONTEXT_FINGERPRINT_MAX_LENGTH,
        true
    ).toLowerCase();
    if (!/^[0-9a-f]+$/.test(publicKeyFingerprint)) {
        throw new Error("publicKeyFingerprint must be a hexadecimal string.");
    }
    const protocolDomain = normalizePayloadContextValue(
        context.protocolDomain ?? DEFAULT_PAYLOAD_PROTOCOL_DOMAIN,
        "protocolDomain",
        PAYLOAD_CONTEXT_DOMAIN_MAX_LENGTH,
        true
    );

    return {
        electionId,
        publicKeyFingerprint,
        protocolDomain,
    };
}

async function hashPayloadContext(context: NormalizedElectionPayloadContext): Promise<Uint8Array> {
    const cryptoApi = assertWebCrypto();
    const aad = buildStructuredAad(PAYLOAD_CONTEXT_LABEL, [
        ["protocolDomain", context.protocolDomain],
        ["electionId", context.electionId],
        ["publicKeyFingerprint", context.publicKeyFingerprint],
    ]);

    return new Uint8Array(await cryptoApi.subtle.digest("SHA-256", asBufferSource(aad)));
}

function buildPayloadInfoV2(contextHash: Uint8Array): Uint8Array {
    return concatBytes(PAYLOAD_INFO_V2_PREFIX, contextHash);
}

function constantTimeEqual(a: Uint8Array, b: Uint8Array): boolean {
    if (a.length !== b.length) {
        return false;
    }
    let diff = 0;
    for (let index = 0; index < a.length; index += 1) {
        diff |= a[index] ^ b[index];
    }
    return diff === 0;
}

async function unwrapElectionPrivateKeyCryptoKey(
    password: string,
    vault: ElectionKeyVault,
    extractable: boolean,
    options: VaultCustodyOptions = {}
): Promise<CryptoKey> {
    assertPassword(password);
    validateVault(vault);

    const cryptoApi = assertWebCrypto();
    const salt = base64DecodeStrict(vault.wrapping.kdf.saltB64, "saltB64");
    const iv = base64DecodeStrict(vault.wrapping.ivB64, "ivB64");
    const ciphertext = base64DecodeStrict(vault.wrapping.ciphertextB64, "ciphertextB64");
    const aad = buildVaultAad(vault);
    const secretMode = resolveVaultSecretMode(vault);
    const wrappingKey = await deriveWrappingKey(
        password,
        salt,
        vault.wrapping.kdf,
        secretMode,
        options
    );

    try {
        return await cryptoApi.subtle.unwrapKey(
            "pkcs8",
            asBufferSource(ciphertext),
            wrappingKey,
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(aad),
            },
            { name: "X25519" },
            extractable,
            ["deriveBits"]
        );
    } catch {
        throw new Error("Failed to unlock election private key. Wrong credentials or tampered backup.");
    } finally {
        wipeBytes(salt);
        wipeBytes(iv);
    }
}

export async function createElectionKeyVault(
    password: string,
    options: VaultCustodyOptions = {}
): Promise<{
    publicKeyHex: string;
    publicKeyFingerprint: string;
    vault: ElectionKeyVault;
}> {
    assertPassword(password);

    const cryptoApi = assertWebCrypto();
    const keyPair = assertCryptoKeyPair(await cryptoApi.subtle.generateKey(
        { name: "X25519" },
        true,
        ["deriveBits"]
    ));

    const publicKeyRaw = new Uint8Array(await cryptoApi.subtle.exportKey("raw", keyPair.publicKey));
    const salt = cryptoApi.getRandomValues(new Uint8Array(PBKDF2_SALT_BYTES));
    const iv = cryptoApi.getRandomValues(new Uint8Array(AES_GCM_IV_BYTES));
    const secretMode: VaultSecretMode = options.custodySecret ? "PASSWORD_PLUS_CUSTODY_SECRET" : "PASSWORD_ONLY";

    const vaultBase: ElectionKeyVault = {
        version: CURRENT_VAULT_VERSION,
        privateKeyAlgorithm: CURRENT_VAULT_ALGORITHM,
        publicKeyRawB64: base64Encode(publicKeyRaw),
        wrapping: {
            cipher: CURRENT_VAULT_CIPHER,
            kdf: {
                name: CURRENT_VAULT_KDF,
                iterations: CURRENT_ARGON2_ITERATIONS,
                memoryKiB: CURRENT_ARGON2_MEMORY_KIB,
                parallelism: CURRENT_ARGON2_PARALLELISM,
                saltB64: base64Encode(salt),
                secretMode,
            },
            ivB64: base64Encode(iv),
            ciphertextB64: "",
        },
    };

    const aad = buildVaultAad(vaultBase);
    const wrappingKey = await deriveWrappingKey(
        password,
        salt,
        vaultBase.wrapping.kdf,
        secretMode,
        options
    );

    try {
        const ciphertext = await cryptoApi.subtle.wrapKey(
            "pkcs8",
            keyPair.privateKey,
            wrappingKey,
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(aad),
            }
        );

        vaultBase.wrapping.ciphertextB64 = base64Encode(new Uint8Array(ciphertext));

        const fingerprintBytes = new Uint8Array(
            await cryptoApi.subtle.digest("SHA-256", asBufferSource(publicKeyRaw))
        );

        return {
            publicKeyHex: bytesToHex(publicKeyRaw),
            publicKeyFingerprint: bytesToHex(fingerprintBytes.slice(0, 8)),
            vault: vaultBase,
        };
    } finally {
        wipeBytes(publicKeyRaw);
        wipeBytes(salt);
        wipeBytes(iv);
    }
}

/**
 * Compatibility API.
 * Prefer unlockElectionPrivateKeyAsCryptoKey() to avoid handling raw secret bytes in application code.
 */
export async function unlockElectionPrivateKey(
    password: string,
    vault: ElectionKeyVault,
    options: VaultCustodyOptions = {}
): Promise<Uint8Array> {
    const cryptoApi = assertWebCrypto();
    const privateKey = await unwrapElectionPrivateKeyCryptoKey(password, vault, true, options);
    const privateKeyPkcs8 = new Uint8Array(await cryptoApi.subtle.exportKey("pkcs8", privateKey));
    return privateKeyPkcs8;
}

export async function unlockElectionPrivateKeyAsCryptoKey(
    password: string,
    vault: ElectionKeyVault,
    options: VaultCustodyOptions = {}
): Promise<CryptoKey> {
    return unwrapElectionPrivateKeyCryptoKey(password, vault, false, options);
}

async function importPublicKey(publicKeyHex: string): Promise<CryptoKey> {
    const cryptoApi = assertWebCrypto();
    const publicKey = hexToBytes(publicKeyHex);
    if (publicKey.length !== X25519_PUBLIC_KEY_BYTES) {
        throw new Error("Election encryption public key must be exactly 32 bytes.");
    }

    try {
        return await cryptoApi.subtle.importKey(
            "raw",
            asBufferSource(publicKey),
            { name: "X25519" },
            false,
            []
        );
    } finally {
        wipeBytes(publicKey);
    }
}

async function importPrivateKey(privateKeyPkcs8: Uint8Array): Promise<CryptoKey> {
    const cryptoApi = assertWebCrypto();
    return cryptoApi.subtle.importKey(
        "pkcs8",
        asBufferSource(privateKeyPkcs8),
        { name: "X25519" },
        false,
        ["deriveBits"]
    );
}

async function derivePayloadKey(sharedSecret: Uint8Array, salt: Uint8Array, info: Uint8Array): Promise<CryptoKey> {
    const cryptoApi = assertWebCrypto();
    const keyMaterial = await cryptoApi.subtle.importKey(
        "raw",
        asBufferSource(sharedSecret),
        "HKDF",
        false,
        ["deriveKey"]
    );

    return cryptoApi.subtle.deriveKey(
        {
            name: "HKDF",
            hash: "SHA-256",
            salt: asBufferSource(salt),
            info: asBufferSource(info),
        },
        keyMaterial,
        { name: "AES-GCM", length: 256 },
        false,
        ["encrypt", "decrypt"]
    );
}

function buildPayloadHeaderV1(ephemeralPublicKey: Uint8Array, salt: Uint8Array, iv: Uint8Array): Uint8Array {
    const header = new Uint8Array(PAYLOAD_HEADER_V1_BYTES);
    let offset = 0;
    header[offset] = PAYLOAD_VERSION_LEGACY;
    offset += 1;
    header.set(ephemeralPublicKey, offset);
    offset += X25519_PUBLIC_KEY_BYTES;
    header.set(salt, offset);
    offset += PAYLOAD_SALT_BYTES;
    header.set(iv, offset);
    return header;
}

function buildPayloadHeaderV2(
    ephemeralPublicKey: Uint8Array,
    salt: Uint8Array,
    iv: Uint8Array,
    contextHash: Uint8Array
): Uint8Array {
    const header = new Uint8Array(PAYLOAD_HEADER_V2_BYTES);
    let offset = 0;
    header[offset] = PAYLOAD_VERSION_CONTEXT_BOUND;
    offset += 1;
    header.set(ephemeralPublicKey, offset);
    offset += X25519_PUBLIC_KEY_BYTES;
    header.set(salt, offset);
    offset += PAYLOAD_SALT_BYTES;
    header.set(iv, offset);
    offset += AES_GCM_IV_BYTES;
    header.set(contextHash, offset);
    return header;
}

type PayloadEncryptionMode =
    | { kind: "legacy" }
    | { kind: "context-bound"; context: NormalizedElectionPayloadContext };

async function encryptElectionPayloadInternal(
    plaintext: Uint8Array | string,
    electionPublicKeyHex: string,
    mode: PayloadEncryptionMode
): Promise<Uint8Array> {
    const cryptoApi = assertWebCrypto();
    const plaintextBytes = typeof plaintext === "string" ? textEncoder.encode(plaintext) : plaintext;
    const recipientPublicKey = await importPublicKey(electionPublicKeyHex);
    const ephemeralKeyPair = assertCryptoKeyPair(
        await cryptoApi.subtle.generateKey({ name: "X25519" }, true, ["deriveBits"])
    );
    const ephemeralPublicKey = new Uint8Array(await cryptoApi.subtle.exportKey("raw", ephemeralKeyPair.publicKey));
    const salt = cryptoApi.getRandomValues(new Uint8Array(PAYLOAD_SALT_BYTES));
    const iv = cryptoApi.getRandomValues(new Uint8Array(AES_GCM_IV_BYTES));

    let sharedSecret: Uint8Array | undefined;
    let contextHash: Uint8Array | undefined;
    let payloadInfo: Uint8Array = PAYLOAD_INFO_V1;

    try {
        sharedSecret = new Uint8Array(
            await cryptoApi.subtle.deriveBits(
                { name: "X25519", public: recipientPublicKey },
                ephemeralKeyPair.privateKey,
                SHARED_SECRET_BYTES * 8
            )
        );
        assertSharedSecretIsNonZero(sharedSecret);

        let header: Uint8Array;
        if (mode.kind === "context-bound") {
            contextHash = await hashPayloadContext(mode.context);
            payloadInfo = buildPayloadInfoV2(contextHash);
            header = buildPayloadHeaderV2(ephemeralPublicKey, salt, iv, contextHash);
        } else {
            header = buildPayloadHeaderV1(ephemeralPublicKey, salt, iv);
        }

        const payloadKey = await derivePayloadKey(sharedSecret, salt, payloadInfo);
        const ciphertext = await cryptoApi.subtle.encrypt(
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(header),
            },
            payloadKey,
            asBufferSource(plaintextBytes)
        );

        const encrypted = new Uint8Array(ciphertext);
        const output = new Uint8Array(header.length + encrypted.length);
        output.set(header, 0);
        output.set(encrypted, header.length);
        return output;
    } finally {
        wipeBytes(sharedSecret);
        wipeBytes(ephemeralPublicKey);
        wipeBytes(salt);
        wipeBytes(iv);
        wipeBytes(contextHash);
        if (payloadInfo !== PAYLOAD_INFO_V1) {
            wipeBytes(payloadInfo);
        }
    }
}

export async function encryptElectionPayload(
    plaintext: Uint8Array | string,
    electionPublicKeyHex: string,
    context: ElectionPayloadContext
): Promise<Uint8Array> {
    const normalizedContext = normalizePayloadContext(context);
    return encryptElectionPayloadInternal(plaintext, electionPublicKeyHex, {
        kind: "context-bound",
        context: normalizedContext,
    });
}

/**
 * Compatibility API for legacy payload format v1.
 * Use encryptElectionPayload() for mandatory context-bound payloads.
 */
export async function encryptElectionPayloadLegacy(
    plaintext: Uint8Array | string,
    electionPublicKeyHex: string
): Promise<Uint8Array> {
    return encryptElectionPayloadInternal(plaintext, electionPublicKeyHex, { kind: "legacy" });
}

async function decryptElectionPayloadInternal(
    ciphertext: Uint8Array,
    privateKeyInput: Uint8Array | CryptoKey,
    context: ElectionPayloadContext | undefined,
    allowLegacyPayload: boolean
): Promise<Uint8Array> {
    if (ciphertext.length <= PAYLOAD_HEADER_V1_BYTES + AES_GCM_TAG_BYTES) {
        throw new Error("Ciphertext is too short.");
    }

    const version = ciphertext[0];
    if (version !== PAYLOAD_VERSION_LEGACY && version !== PAYLOAD_VERSION_CONTEXT_BOUND) {
        throw new Error(`Unsupported encrypted payload version: ${version}`);
    }
    if (version === PAYLOAD_VERSION_LEGACY && !allowLegacyPayload) {
        throw new Error(
            "Legacy payload v1 is disabled in strict mode. " +
            "Use decryptElectionPayloadLegacy() for compatibility."
        );
    }

    const headerLength = version === PAYLOAD_VERSION_CONTEXT_BOUND ? PAYLOAD_HEADER_V2_BYTES : PAYLOAD_HEADER_V1_BYTES;
    if (ciphertext.length <= headerLength + AES_GCM_TAG_BYTES) {
        throw new Error("Ciphertext is too short.");
    }

    const header = ciphertext.slice(0, headerLength);
    const ephemeralPublicKeyRaw = ciphertext.slice(1, 1 + X25519_PUBLIC_KEY_BYTES);
    const salt = ciphertext.slice(1 + X25519_PUBLIC_KEY_BYTES, 1 + X25519_PUBLIC_KEY_BYTES + PAYLOAD_SALT_BYTES);
    const iv = ciphertext.slice(1 + X25519_PUBLIC_KEY_BYTES + PAYLOAD_SALT_BYTES, PAYLOAD_HEADER_V1_BYTES);
    const body = ciphertext.slice(headerLength);

    let payloadInfo: Uint8Array = PAYLOAD_INFO_V1;
    let contextHash: Uint8Array | undefined;
    let expectedContextHash: Uint8Array | undefined;

    const cryptoApi = assertWebCrypto();
    const privateKey =
        privateKeyInput instanceof Uint8Array
            ? await importPrivateKey(privateKeyInput)
            : privateKeyInput;
    assertPrivateX25519Key(privateKey);

    let sharedSecret: Uint8Array | undefined;
    try {
        if (version === PAYLOAD_VERSION_CONTEXT_BOUND) {
            if (!context) {
                throw new Error("Context is required to decrypt version 2 payloads.");
            }

            contextHash = ciphertext.slice(PAYLOAD_HEADER_V1_BYTES, PAYLOAD_HEADER_V2_BYTES);
            if (contextHash.length !== PAYLOAD_CONTEXT_HASH_BYTES) {
                throw new Error("Invalid payload context hash length.");
            }

            expectedContextHash = await hashPayloadContext(normalizePayloadContext(context));
            if (!constantTimeEqual(contextHash, expectedContextHash)) {
                throw new Error("Encrypted payload context mismatch.");
            }
            payloadInfo = buildPayloadInfoV2(contextHash);
        }

        const publicKey = await cryptoApi.subtle.importKey(
            "raw",
            asBufferSource(ephemeralPublicKeyRaw),
            { name: "X25519" },
            false,
            []
        );

        sharedSecret = new Uint8Array(
            await cryptoApi.subtle.deriveBits(
                { name: "X25519", public: publicKey },
                privateKey,
                SHARED_SECRET_BYTES * 8
            )
        );
        assertSharedSecretIsNonZero(sharedSecret);

        const payloadKey = await derivePayloadKey(sharedSecret, salt, payloadInfo);
        const plaintext = await cryptoApi.subtle.decrypt(
            {
                name: "AES-GCM",
                iv: asBufferSource(iv),
                additionalData: asBufferSource(header),
            },
            payloadKey,
            asBufferSource(body)
        );

        return new Uint8Array(plaintext);
    } catch (error) {
        if (error instanceof Error && /context mismatch/i.test(error.message)) {
            throw error;
        }
        throw new Error("Failed to decrypt election payload.");
    } finally {
        wipeBytes(sharedSecret);
        wipeBytes(ephemeralPublicKeyRaw);
        wipeBytes(salt);
        wipeBytes(iv);
        wipeBytes(contextHash);
        wipeBytes(expectedContextHash);
        if (payloadInfo !== PAYLOAD_INFO_V1) {
            wipeBytes(payloadInfo);
        }
    }
}

export async function decryptElectionPayload(
    ciphertext: Uint8Array,
    privateKeyInput: Uint8Array | CryptoKey,
    context: ElectionPayloadContext
): Promise<Uint8Array> {
    return decryptElectionPayloadInternal(ciphertext, privateKeyInput, context, false);
}

/**
 * Compatibility API for legacy payload format v1.
 * Prefer decryptElectionPayload() with context-bound payloads.
 */
export async function decryptElectionPayloadLegacy(
    ciphertext: Uint8Array,
    privateKeyInput: Uint8Array | CryptoKey
): Promise<Uint8Array> {
    return decryptElectionPayloadInternal(ciphertext, privateKeyInput, undefined, true);
}

function isObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

export function parseStoredElectionKeyVault(json: string): StoredElectionKeyVault {
    let parsed: unknown;
    try {
        parsed = JSON.parse(json);
    } catch {
        throw new Error("Backup JSON is not valid.");
    }

    if (!isObject(parsed) || !isObject(parsed.vault)) {
        throw new Error("Backup JSON does not match the expected election key format.");
    }

    const record: StoredElectionKeyVault = {
        electionPublicId: normalizeUuid(String(parsed.electionPublicId ?? ""), "electionPublicId"),
        electionTitle: typeof parsed.electionTitle === "string" ? parsed.electionTitle : undefined,
        createdAt: String(parsed.createdAt ?? ""),
        publicKeyHex: String(parsed.publicKeyHex ?? "").toLowerCase(),
        vault: parsed.vault as ElectionKeyVault,
    };

    if (!/^[0-9a-f]{64}$/.test(record.publicKeyHex)) {
        throw new Error("Backup publicKeyHex must be a 32-byte hexadecimal string.");
    }

    if (Number.isNaN(Date.parse(record.createdAt))) {
        throw new Error("Backup createdAt must be a valid ISO timestamp.");
    }

    validateVault(record.vault);

    if (getVaultPublicKeyHex(record.vault) !== record.publicKeyHex) {
        throw new Error("Backup public key metadata does not match the encrypted vault.");
    }

    return record;
}

export function serializeStoredElectionKeyVault(record: StoredElectionKeyVault): string {
    normalizeUuid(record.electionPublicId, "electionPublicId");
    if (!/^[0-9a-f]{64}$/.test(record.publicKeyHex)) {
        throw new Error("publicKeyHex must be a 32-byte hexadecimal string.");
    }
    validateVault(record.vault);

    return JSON.stringify(record, null, 2);
}

export function createStoredElectionKeyVault(input: {
    electionPublicId: string;
    electionTitle?: string;
    publicKeyHex: string;
    vault: ElectionKeyVault;
}): StoredElectionKeyVault {
    const electionPublicId = normalizeUuid(input.electionPublicId, "electionPublicId");
    const publicKeyHex = input.publicKeyHex.toLowerCase();
    if (!/^[0-9a-f]{64}$/.test(publicKeyHex)) {
        throw new Error("publicKeyHex must be a 32-byte hexadecimal string.");
    }
    if (getVaultPublicKeyHex(input.vault) !== publicKeyHex) {
        throw new Error("Vault public key does not match the provided publicKeyHex.");
    }

    return {
        electionPublicId,
        electionTitle: input.electionTitle?.trim() || undefined,
        publicKeyHex,
        createdAt: new Date().toISOString(),
        vault: input.vault,
    };
}
