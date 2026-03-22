import { Identity } from "@semaphore-protocol/identity";
import type { MerkleProof } from "@semaphore-protocol/group";
import { generateProof, type SemaphoreProof } from "@semaphore-protocol/proof";
import type { SnarkArtifacts } from "@zk-kit/artifacts";
import { isAddress, keccak256, toHex } from "viem";

import { fetchElectionMerkleProof, toMerkleProof } from "./group";

export type CiphertextLike = Uint8Array | `0x${string}`;

export type FieldLike = bigint | number | string;
const SNARK_SCALAR_FIELD = 21888242871839275222246405745257275088548364400416034343698204186575808495617n;

function parseHexBytes(hexValue: string): Uint8Array {
    const hex = hexValue.startsWith("0x") ? hexValue.slice(2) : hexValue;

    if (hex.length === 0) {
        return new Uint8Array(0);
    }

    if (hex.length % 2 !== 0) {
        throw new Error("Hex string must have even length.");
    }

    const out = new Uint8Array(hex.length / 2);

    for (let i = 0; i < out.length; i++) {
        const byteHex = hex.slice(i * 2, i * 2 + 2);
        const byte = Number.parseInt(byteHex, 16);

        if (Number.isNaN(byte)) {
            throw new TypeError(`Invalid hex byte: ${byteHex}`);
        }

        out[i] = byte;
    }

    return out;
}

function normalizeCiphertextBytes(ciphertext: CiphertextLike): Uint8Array {
    if (ciphertext instanceof Uint8Array) {
        return ciphertext;
    }

    return parseHexBytes(ciphertext);
}

//! This is kept aligned with Solidity contract: uint256(keccak256(ciphertextBytes)) >> 8.
export function hashCiphertextToField(ciphertext: CiphertextLike): bigint {
    const bytes = normalizeCiphertextBytes(ciphertext);
    const digestHex = keccak256(toHex(bytes));
    return BigInt(digestHex) >> 8n;
}

export function normalizeFieldValue(value: FieldLike, label: string): bigint {
    let out: bigint;

    if (typeof value === "bigint") {
        if (value < 0n) {
            throw new Error(`${label} cannot be negative.`);
        }
        out = value;
    } else if (typeof value === "number") {
        if (!Number.isSafeInteger(value) || value < 0) {
            throw new Error(`${label} must be a non-negative safe integer.`);
        }
        out = BigInt(value);
    } else {
        const trimmed = value.trim();

        if (/^\d+$/.test(trimmed)) {
            out = BigInt(trimmed);
        } else if (/^0x[0-9a-fA-F]+$/.test(trimmed)) {
            out = BigInt(trimmed);
        } else {
            throw new Error(
                `${label} must be a canonical non-negative decimal string, bigint, safe integer, or 0x-prefixed hex string.`
            );
        }
    }

    if (out >= SNARK_SCALAR_FIELD) {
        throw new Error(
            `${label} must be < SNARK scalar field (${SNARK_SCALAR_FIELD.toString()}).`
        );
    }

    return out;
}

function assertMerkleProofMatchesIdentity(
    identity: Identity,
    merkleProof: MerkleProof
): void {
    if (merkleProof.leaf !== identity.commitment) {
        throw new Error(
            `Merkle proof leaf mismatch. Got leaf=${merkleProof.leaf.toString()} ` +
            `but identity.commitment=${identity.commitment.toString()}`
        );
    }
}

function assertMerkleDepthMatchesProof(
    merkleDepth: number,
    merkleProof: MerkleProof
): void {
    if (!Number.isInteger(merkleDepth) || merkleDepth <= 0) {
        throw new Error(`Invalid merkleDepth: ${merkleDepth}`);
    }

    if (merkleProof.siblings.length > merkleDepth) {
        throw new Error(
            `Merkle proof depth mismatch. merkleDepth=${merkleDepth} but siblings.length=${merkleProof.siblings.length}`
        );
    }
}

export type CreateSemaphoreProofParams = {
    identity: Identity;
    merkleProof: MerkleProof;
    merkleDepth: number;

    // Usually hashCiphertextToField(ciphertext).
    message: FieldLike;

    // Must match contract externalNullifier.
    scope: FieldLike;

    snarkArtifacts: SnarkArtifacts;
};

export async function createSemaphoreProof(
    params: CreateSemaphoreProofParams
): Promise<SemaphoreProof> {
    const {
        identity,
        merkleProof,
        merkleDepth,
        message,
        scope,
        snarkArtifacts,
    } = params;

    assertMerkleProofMatchesIdentity(identity, merkleProof);
    assertMerkleDepthMatchesProof(merkleDepth, merkleProof);

    const normalizedMessage = normalizeFieldValue(message, "message");
    const normalizedScope = normalizeFieldValue(scope, "scope");

    return generateProof(
        identity,
        merkleProof,
        normalizedMessage,
        normalizedScope,
        merkleDepth,
        snarkArtifacts
    );
}

export type CreateElectionVoteProofParams = {
    identity: Identity;
    merkleProof: MerkleProof;
    merkleDepth: number;
    ciphertext: CiphertextLike;
    externalNullifier: FieldLike;
    snarkArtifacts: SnarkArtifacts;
};

export async function createElectionVoteProof(
    params: CreateElectionVoteProofParams
): Promise<SemaphoreProof> {
    const {
        identity,
        merkleProof,
        merkleDepth,
        ciphertext,
        externalNullifier,
        snarkArtifacts,
    } = params;

    const message = hashCiphertextToField(ciphertext);
    const scope = normalizeFieldValue(externalNullifier, "externalNullifier");

    return createSemaphoreProof({
        identity,
        merkleProof,
        merkleDepth,
        message,
        scope,
        snarkArtifacts,
    });
}

export type CreateElectionVoteProofViaFastifyParams = {
    fastifyBaseUrl: string;
    electionAddress: string;
    identity: Identity;
    ciphertext: CiphertextLike;
    externalNullifier: FieldLike;
    snarkArtifacts: SnarkArtifacts;

    // Circuit depth from local artifacts. Missing siblings are zero-padded by @semaphore-protocol/proof.
    circuitDepth?: number;

    headers?: Record<string, string>;
    signal?: AbortSignal;
};

export async function createElectionVoteProofViaFastify(
    params: CreateElectionVoteProofViaFastifyParams
): Promise<SemaphoreProof> {
    const {
        fastifyBaseUrl,
        electionAddress,
        identity,
        ciphertext,
        externalNullifier,
        snarkArtifacts,
        circuitDepth,
        headers,
        signal,
    } = params;

    if (!isAddress(electionAddress)) {
        throw new Error(`Invalid electionAddress: ${electionAddress}`);
    }

    const commitmentDec = identity.commitment.toString(); // server expects decimal

    const dto = await fetchElectionMerkleProof({
        fastifyBaseUrl,
        electionAddress,
        commitmentDec,
        headers,
        signal,
    });

    const merkleProof = toMerkleProof(dto);
    const effectiveMerkleDepth = Math.max(circuitDepth ?? 0, dto.expectedDepth);

    // Fastify validates onChainRoot === proof.root and returns 409 on mismatch.
    return createElectionVoteProof({
        identity,
        merkleProof,
        merkleDepth: effectiveMerkleDepth,
        ciphertext,
        externalNullifier,
        snarkArtifacts,
    });
}
