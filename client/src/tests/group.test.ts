import assert from "node:assert/strict";
import { afterEach, describe, it } from "mocha";

import {
    FastifyMerkleProofResponseSchema,
    fetchElectionMerkleProof,
    toMerkleProof,
} from "../semaphore/group";

const ORIGINAL_FETCH = globalThis.fetch;

describe("semaphore/group", () => {
    afterEach(() => {
        globalThis.fetch = ORIGINAL_FETCH;
    });

    it("converts dto bigint-like values into MerkleProof", () => {
        const dto = FastifyMerkleProofResponseSchema.parse({
            groupId: "g",
            expectedDepth: 2,
            root: "0x10",
            leaf: "15",
            siblings: ["1", "0x02"],
            index: 1,
        });

        const proof = toMerkleProof(dto);

        assert.equal(proof.root, 16n);
        assert.equal(proof.leaf, 15n);
        assert.deepEqual(proof.siblings, [1n, 2n]);
    });

    it("rejects unsafe index values", () => {
        assert.throws(
            () =>
                FastifyMerkleProofResponseSchema.parse({
                    groupId: "g",
                    expectedDepth: 2,
                    root: "1",
                    leaf: "1",
                    siblings: ["1", "1"],
                    index: Number.MAX_SAFE_INTEGER + 1,
                }),
            /number/i
        );
    });

    it("normalizes address and commitment in fetch request", async () => {
        let capturedUrl = "";
        globalThis.fetch = async (input) => {
            capturedUrl = String(input);
            return new Response(
                JSON.stringify({
                    groupId: "g",
                    expectedDepth: 1,
                    root: "1",
                    leaf: "1",
                    siblings: ["2"],
                    index: 0,
                }),
                { status: 200, headers: { "content-type": "application/json" } }
            );
        };

        await fetchElectionMerkleProof({
            fastifyBaseUrl: "https://example.org/",
            electionAddress: "0xabc0000000000000000000000000000000000000",
            commitmentDec: "000123",
        });

        assert.match(
            capturedUrl,
            /\/elections\/0xabc0000000000000000000000000000000000000\/proof/
        );
        assert.match(capturedUrl, /[?&]commitment=123(?:&|$)/);
    });
});
