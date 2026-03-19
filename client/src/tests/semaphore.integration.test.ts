import assert from "node:assert/strict";
import { afterEach, describe, it } from "mocha";

import {
    getSemaphoreSnarkArtifacts,
    SEMAPHORE_ARTIFACT_DEPTH,
} from "../semaphore/artifacts";
import {
    createIdentityVault,
    deriveElectionIdentityFromVault,
    electionKeyFromExternalNullifier,
} from "../semaphore/identity";
import {
    createElectionVoteProofViaFastify,
    hashCiphertextToField,
} from "../semaphore/proof";

const ORIGINAL_FETCH = globalThis.fetch;

describe("semaphore integration", () => {
    afterEach(() => {
        globalThis.fetch = ORIGINAL_FETCH;
    });

    it("wires identity + group fetch + proof pipeline end-to-end with local artifacts", async function () {
        this.timeout(10_000);

        const password = "very-strong-password";
        const externalNullifier = 42n;
        const ciphertext = new Uint8Array([1, 2, 3, 4]);
        const electionKey = electionKeyFromExternalNullifier(externalNullifier);
        const vault = await createIdentityVault(password);
        const identity = await deriveElectionIdentityFromVault(
            password,
            vault,
            electionKey
        );
        const snarkArtifacts = getSemaphoreSnarkArtifacts();

        let capturedUrl = "";
        globalThis.fetch = async (input) => {
            capturedUrl = String(input);
            return new Response(
                JSON.stringify({
                    groupId: "group-1",
                    expectedDepth: SEMAPHORE_ARTIFACT_DEPTH,
                    root: "0",
                    leaf: identity.commitment.toString(),
                    siblings: Array.from(
                        { length: SEMAPHORE_ARTIFACT_DEPTH },
                        () => "0"
                    ),
                    index: 0,
                }),
                { status: 200, headers: { "content-type": "application/json" } }
            );
        };

        const proof = await createElectionVoteProofViaFastify({
            fastifyBaseUrl: "https://example.org",
            electionAddress: "0xabc0000000000000000000000000000000000000",
            identity,
            ciphertext,
            externalNullifier,
            snarkArtifacts,
        });

        assert.match(
            capturedUrl,
            new RegExp(`[?&]commitment=${identity.commitment.toString()}(?:&|$)`)
        );
        assert.match(
            capturedUrl,
            /\/elections\/0xabc0000000000000000000000000000000000000\/proof/
        );
        assert.equal(proof.merkleTreeDepth, SEMAPHORE_ARTIFACT_DEPTH);
        assert.equal(proof.scope, externalNullifier.toString());
        assert.equal(proof.message, hashCiphertextToField(ciphertext).toString());
        assert.equal(proof.points.length, 8);
        assert.match(proof.nullifier, /^\d+$/);
    });
});
