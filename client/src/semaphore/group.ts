import type { MerkleProof } from "@semaphore-protocol/group";
import { getAddress, isAddress } from "viem";
import { z } from "zod";

/**
 * This DTO is returned by the Fastify proof-service.
 *
 * Important invariants:
 * - commitment query parameter must be canonical decimal
 * - response bigint-like fields must be non-negative
 * - siblings length must equal expectedDepth
 */
export const FastifyMerkleProofResponseSchema = z
    .object({
        groupId: z.string().trim().min(1),
        expectedDepth: z.number().int().positive().max(64),
        root: z.string().trim().min(1),
        leaf: z.string().trim().min(1),
        siblings: z.array(z.string().trim().min(1)),
        index: z.number().int().nonnegative().safe(),
    })
    .superRefine((v, ctx) => {
        if (v.siblings.length !== v.expectedDepth) {
            ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: `siblings length (${v.siblings.length}) must equal expectedDepth (${v.expectedDepth})`,
            });
        }

        // Optional structural sanity check for binary tree indices.
        // index should fit in the depth.
        if (v.expectedDepth < 53) {
            const maxIndex = 2 ** v.expectedDepth - 1;
            if (v.index > maxIndex) {
                ctx.addIssue({
                    code: z.ZodIssueCode.custom,
                    message: `index (${v.index}) exceeds maximum index for depth ${v.expectedDepth}`,
                });
            }
        }
    });

export type FastifyMerkleProofResponse = z.infer<
    typeof FastifyMerkleProofResponseSchema
>;

export type FetchElectionMerkleProofParams = {
    fastifyBaseUrl: string;
    electionAddress: string;

    /**
     * Canonical non-negative decimal commitment string.
     * Example: "1234567890"
     */
    commitmentDec: string;

    headers?: Record<string, string>;
    signal?: AbortSignal;
};

export async function fetchElectionMerkleProof(
    params: FetchElectionMerkleProofParams
): Promise<FastifyMerkleProofResponse> {
    const { fastifyBaseUrl, electionAddress, commitmentDec, headers, signal } =
        params;

    const normalizedElectionAddress = normalizeAddress(electionAddress);
    const normalizedCommitment = normalizeDecimalString(
        commitmentDec,
        "commitmentDec"
    );

    const base = fastifyBaseUrl.replace(/\/+$/, "");
    const url = new URL(
        `${base}/elections/${encodeURIComponent(normalizedElectionAddress)}/proof`
    );
    url.searchParams.set("commitment", normalizedCommitment);

    const res = await fetch(url.toString(), {
        method: "GET",
        headers: { Accept: "application/json", ...headers },
        signal,
    });

    if (!res.ok) {
        const text = await safeReadText(res);
        throw new Error(
            `Fastify /elections/:address/proof failed (${res.status}): ${text}`
        );
    }

    const json: unknown = await res.json();
    return FastifyMerkleProofResponseSchema.parse(json);
}

/**
 * Convert Fastify DTO into the Semaphore MerkleProof type expected by
 * @semaphore-protocol/proof.
 */
export function toMerkleProof(dto: FastifyMerkleProofResponse): MerkleProof {
    return {
        root: parseNonNegativeBigIntLike(dto.root, "root"),
        leaf: parseNonNegativeBigIntLike(dto.leaf, "leaf"),
        index: dto.index,
        siblings: dto.siblings.map((s, i) =>
            parseNonNegativeBigIntLike(s, `siblings[${i}]`)
        ),
    };
}

async function safeReadText(res: Response): Promise<string> {
    try {
        return await res.text();
    } catch {
        return "<unreadable>";
    }
}

/**
 * Accept only canonical non-negative decimal strings.
 * This matches your Fastify server expectation.
 */
function normalizeDecimalString(value: string, label: string): string {
    if (typeof value !== "string") {
        throw new TypeError(`${label} must be a string.`);
    }

    const trimmed = value.trim();
    if (!/^\d+$/.test(trimmed)) {
        throw new TypeError(
            `${label} must be a canonical non-negative decimal string.`
        );
    }

    // Normalize leading zeros, but preserve "0".
    return trimmed.replace(/^0+(?=\d)/, "");
}

/**
 * Parse bigint-like values, but reject negatives.
 * Supports decimal and 0x-prefixed hex for response fields.
 */
function parseNonNegativeBigIntLike(
    value: string | number | bigint,
    label: string
): bigint {
    let out: bigint;

    if (typeof value === "bigint") {
        out = value;
    } else if (typeof value === "number") {
        if (!Number.isInteger(value)) {
            throw new TypeError(`${label} must be an integer number, got ${value}`);
        }
        out = BigInt(value);
    } else {
        const trimmed = value.trim();
        if (trimmed.length === 0) {
            throw new TypeError(`${label} cannot be an empty bigint string.`);
        }

        try {
            out = BigInt(trimmed); // supports decimal and 0x-prefixed hex
        } catch {
            throw new TypeError(`${label} is not a valid bigint-like value: ${value}`);
        }
    }

    if (out < 0n) {
        throw new TypeError(`${label} cannot be negative.`);
    }

    return out;
}

/**
 * Canonicalize election contract addresses for transport.
 *
 * Your backend normalizes addresses to lowercase before persistence,
 * so this function intentionally returns lowercase.
 */
function normalizeAddress(address: string): string {
    if (!isAddress(address)) {
        throw new Error(`Invalid electionAddress: ${address}`);
    }

    return getAddress(address).toLowerCase();
}
