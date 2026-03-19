import assert from "node:assert/strict";
import { describe, it } from "mocha";

import {
    createElectionKeyVault,
    createStoredElectionKeyVault,
    decryptElectionPayload,
    decryptElectionPayloadLegacy,
    encryptElectionPayload,
    encryptElectionPayloadLegacy,
    getVaultPublicKeyHex,
    parseStoredElectionKeyVault,
    serializeStoredElectionKeyVault,
    unlockElectionPrivateKey,
    unlockElectionPrivateKeyAsCryptoKey,
} from "@/crypto/electionKeys";

describe("shared/crypto/electionKeys", function (this: Mocha.Suite) {
    this.timeout(30_000);
    it("creates a vault and unlocks the same private key material", async () => {
        const { publicKeyHex, vault } = await createElectionKeyVault("very-strong-election-password");

        assert.equal(getVaultPublicKeyHex(vault), publicKeyHex);
        assert.equal(vault.wrapping.kdf.name, "ARGON2ID");
        if (vault.wrapping.kdf.name !== "ARGON2ID") {
            throw new Error("Expected ARGON2ID vault KDF metadata.");
        }
        assert.equal(vault.wrapping.kdf.iterations, 3);
        assert.equal(vault.wrapping.kdf.memoryKiB, 64 * 1024);
        assert.equal(vault.wrapping.kdf.parallelism, 1);

        const privateKey = await unlockElectionPrivateKey("very-strong-election-password", vault);
        assert.equal(privateKey.length, 48);
    });

    it("rejects the wrong password", async () => {
        const { vault } = await createElectionKeyVault("very-strong-election-password");

        await assert.rejects(
            () => unlockElectionPrivateKey("wrong-password-123", vault),
            /wrong password|tampered backup/i
        );
    });

    it("encrypts and decrypts election payloads", async () => {
        const plaintext = new TextEncoder().encode("candidate:2");
        const { publicKeyHex, publicKeyFingerprint, vault } = await createElectionKeyVault(
            "very-strong-election-password"
        );
        const privateKey = await unlockElectionPrivateKeyAsCryptoKey("very-strong-election-password", vault);
        const context = {
            electionId: "1ed53c6b-1b39-4c3f-a1ab-85e2026337f0",
            publicKeyFingerprint,
        };

        const ciphertext = await encryptElectionPayload(plaintext, publicKeyHex, context);
        assert.equal(ciphertext[0], 2);
        const decrypted = await decryptElectionPayload(ciphertext, privateKey, context);

        assert.deepEqual(Array.from(decrypted), Array.from(plaintext));
    });

    it("rejects context-bound payload decryptions with mismatched context", async () => {
        const plaintext = new TextEncoder().encode("candidate:1");
        const { publicKeyHex, publicKeyFingerprint, vault } = await createElectionKeyVault(
            "very-strong-election-password"
        );
        const privateKey = await unlockElectionPrivateKeyAsCryptoKey("very-strong-election-password", vault);
        const context = {
            electionId: "1ed53c6b-1b39-4c3f-a1ab-85e2026337f0",
            publicKeyFingerprint,
        };

        const ciphertext = await encryptElectionPayload(plaintext, publicKeyHex, context);

        await assert.rejects(
            () =>
                decryptElectionPayload(ciphertext, privateKey, {
                    ...context,
                    electionId: "5f0e4f23-b5b0-49bd-8c02-216ece8ac5d6",
                }),
            /context mismatch/i
        );
    });

    it("supports legacy payloads without explicit context", async () => {
        const plaintext = new TextEncoder().encode("candidate:3");
        const { publicKeyHex, vault } = await createElectionKeyVault("very-strong-election-password");
        const privateKey = await unlockElectionPrivateKey("very-strong-election-password", vault);

        const ciphertext = await encryptElectionPayloadLegacy(plaintext, publicKeyHex);
        const decrypted = await decryptElectionPayloadLegacy(ciphertext, privateKey);
        assert.equal(ciphertext[0], 1);

        assert.deepEqual(Array.from(decrypted), Array.from(plaintext));
    });

    it("rejects decrypting legacy payloads through strict API", async () => {
        const plaintext = new TextEncoder().encode("candidate:4");
        const { publicKeyHex, publicKeyFingerprint, vault } = await createElectionKeyVault(
            "very-strong-election-password"
        );
        const privateKey = await unlockElectionPrivateKeyAsCryptoKey("very-strong-election-password", vault);
        const legacyCiphertext = await encryptElectionPayloadLegacy(plaintext, publicKeyHex);

        await assert.rejects(
            () =>
                decryptElectionPayload(legacyCiphertext, privateKey, {
                    electionId: "1ed53c6b-1b39-4c3f-a1ab-85e2026337f0",
                    publicKeyFingerprint,
                }),
            /legacy payload v1 is disabled/i
        );
    });

    it("returns non-extractable private keys from unlockElectionPrivateKeyAsCryptoKey", async () => {
        const { vault } = await createElectionKeyVault("very-strong-election-password");
        const privateKey = await unlockElectionPrivateKeyAsCryptoKey("very-strong-election-password", vault);

        assert.equal(privateKey.type, "private");
        assert.equal(privateKey.extractable, false);
        assert.equal(privateKey.algorithm.name, "X25519");
    });

    it("rejects malformed non-canonical base64 in vault metadata", async () => {
        const { vault } = await createElectionKeyVault("very-strong-election-password");
        const tampered = {
            ...vault,
            wrapping: {
                ...vault.wrapping,
                kdf: {
                    ...vault.wrapping.kdf,
                    saltB64: "%%%not-base64%%%",
                },
            },
        };

        await assert.rejects(
            () => unlockElectionPrivateKey("very-strong-election-password", tampered),
            /base64/i
        );
    });

    it("round-trips stored vault backups", async () => {
        const { publicKeyHex, vault } = await createElectionKeyVault("very-strong-election-password");
        const stored = createStoredElectionKeyVault({
            electionPublicId: "1ed53c6b-1b39-4c3f-a1ab-85e2026337f0",
            electionTitle: "Board Election",
            publicKeyHex,
            vault,
        });

        const parsed = parseStoredElectionKeyVault(serializeStoredElectionKeyVault(stored));

        assert.equal(parsed.electionPublicId, stored.electionPublicId);
        assert.equal(parsed.publicKeyHex, stored.publicKeyHex);
        assert.equal(parsed.electionTitle, stored.electionTitle);
    });
});
