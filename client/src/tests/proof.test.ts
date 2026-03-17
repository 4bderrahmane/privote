import assert from "node:assert/strict";
import { afterEach, describe, it } from "mocha";
import { Identity } from "@semaphore-protocol/identity";

import {
    createElectionVoteProofViaFastify,
    createSemaphoreProof,
    hashCiphertextToField,
    normalizeFieldValue,
} from "../semaphore/proof";

const ORIGINAL_FETCH = globalThis.fetch;

describe("semaphore/proof", () => {
    afterEach(() => {
        globalThis.fetch = ORIGINAL_FETCH;
    });

    it("hashCiphertextToField is deterministic", () => {
        const c = new Uint8Array([1, 2, 3, 4, 5]);
        const h1 = hashCiphertextToField(c);
        const h2 = hashCiphertextToField(c);
        assert.equal(h1, h2);
    });

    it("normalizeFieldValue rejects values outside SNARK field", () => {
        const field =
            21888242871839275222246405745257275088548364400416034343698204186575808495617n;
        assert.throws(() => normalizeFieldValue(field, "message"), /SNARK scalar field/i);
    });

    it("createSemaphoreProof rejects depth mismatch before proof generation", async () => {
        const identity = new Identity("test-key");

        await assert.rejects(
            () =>
                createSemaphoreProof({
                    identity,
                    merkleProof: {
                        root: 1n,
                        leaf: identity.commitment,
                        index: 0,
                        siblings: [2n, 3n],
                    },
                    merkleDepth: 1,
                    message: 1n,
                    scope: 2n,
                    snarkArtifacts: {} as never,
                }),
            /depth mismatch/i
        );
    });

    it("createElectionVoteProofViaFastify rejects leaf mismatch", async () => {
        const identity = new Identity("test-key-2");

        globalThis.fetch = async () =>
            new Response(
                JSON.stringify({
                    groupId: "g",
                    expectedDepth: 1,
                    root: "1",
                    leaf: "999999999999",
                    siblings: ["2"],
                    index: 0,
                }),
                { status: 200, headers: { "content-type": "application/json" } }
            );

        await assert.rejects(
            () =>
                createElectionVoteProofViaFastify({
                    fastifyBaseUrl: "https://example.org",
                    electionAddress: "0xabc0000000000000000000000000000000000000",
                    identity,
                    ciphertext: new Uint8Array([1, 2, 3]),
                    externalNullifier: 42n,
                    snarkArtifacts: {} as never,
                }),
            /leaf mismatch/i
        );
    });
});
