import assert from "node:assert/strict";
import { afterEach, describe, it } from "mocha";

import {
    createIdentityVault,
    deriveElectionIdentityFromVault,
    electionKeyFromExternalNullifier,
} from "../semaphore/identity";
import { createElectionVoteProofViaFastify } from "../semaphore/proof";

const ORIGINAL_FETCH = globalThis.fetch;

describe("semaphore integration", () => {
    afterEach(() => {
        globalThis.fetch = ORIGINAL_FETCH;
    });

    it("wires identity + group fetch + proof pipeline end-to-end", async () => {
        const password = "very-strong-password";
        const externalNullifier = 42n;
        const electionKey = electionKeyFromExternalNullifier(externalNullifier);
        const vault = await createIdentityVault(password);
        const identity = await deriveElectionIdentityFromVault(
            password,
            vault,
            electionKey
        );

        let capturedUrl = "";
        globalThis.fetch = async (input) => {
            capturedUrl = String(input);
            return new Response(
                JSON.stringify({
                    groupId: "group-1",
                    expectedDepth: 1,
                    root: "123",
                    leaf: identity.commitment.toString(),
                    siblings: ["456"],
                    index: 0,
                }),
                { status: 200, headers: { "content-type": "application/json" } }
            );
        };

        // We intentionally pass invalid snarkArtifacts to stop at prover boundary.
        // If the integration before proof generation is wrong, it would fail earlier
        // with leaf/depth/address mismatch instead.
        await assert.rejects(async () => {
            await createElectionVoteProofViaFastify({
                fastifyBaseUrl: "https://example.org",
                electionAddress: "0xabc0000000000000000000000000000000000000",
                identity,
                ciphertext: new Uint8Array([1, 2, 3, 4]),
                externalNullifier,
                snarkArtifacts: {} as never,
            });
        });

        assert.match(
            capturedUrl,
            new RegExp(`[?&]commitment=${identity.commitment.toString()}(?:&|$)`)
        );
        assert.match(
            capturedUrl,
            /\/elections\/0xabc0000000000000000000000000000000000000\/proof/
        );
    });
});
