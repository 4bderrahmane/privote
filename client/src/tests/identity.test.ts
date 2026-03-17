import assert from "node:assert/strict";
import { describe, it } from "mocha";

import {
    createIdentityVault,
    decryptMasterSecret,
    deriveElectionIdentityFromMasterSecret,
    deriveElectionIdentityFromVault,
    electionKeyFromExternalNullifier,
} from "../semaphore/identity";

describe("semaphore/identity", () => {
    it("creates and decrypts a vault", async () => {
        const vault = await createIdentityVault("very-strong-password");
        const secret = await decryptMasterSecret("very-strong-password", vault);

        assert.equal(secret.length, 32);
        assert.notDeepEqual(Array.from(secret), new Array(32).fill(0));
    });

    it("derives stable identity for same vault + election key", async () => {
        const password = "very-strong-password";
        const key = electionKeyFromExternalNullifier("00042");
        const vault = await createIdentityVault(password);

        const id1 = await deriveElectionIdentityFromVault(password, vault, key);
        const id2 = await deriveElectionIdentityFromVault(password, vault, key);

        assert.equal(id1.commitment, id2.commitment);
    });

    it("derives different identities for different election keys", async () => {
        const password = "very-strong-password";
        const vault = await createIdentityVault(password);
        const secret = await decryptMasterSecret(password, vault);

        const id1 = await deriveElectionIdentityFromMasterSecret(
            secret,
            electionKeyFromExternalNullifier(1)
        );
        const id2 = await deriveElectionIdentityFromMasterSecret(
            secret,
            electionKeyFromExternalNullifier(2)
        );

        assert.notEqual(id1.commitment, id2.commitment);
    });

    it("rejects tampered iteration count below allowed minimum", async () => {
        const password = "very-strong-password";
        const vault = await createIdentityVault(password);
        const tampered = {
            ...vault,
            kdf: {
                ...vault.kdf,
                iterations: 1,
            },
        };

        await assert.rejects(
            () => decryptMasterSecret(password, tampered),
            /out of allowed range/i
        );
    });
});
