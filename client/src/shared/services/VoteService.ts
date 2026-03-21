import axios from "axios";
import api from "@/api.ts";
import type { CastVoteInput, CastVoteReceipt } from "../types/vote";

const ELECTIONS_ENDPOINT = "/elections";

type ProblemDetailLike = {
    detail?: string;
    message?: string;
};

function asErrorMessage(error: unknown, fallback: string) {
    if (axios.isAxiosError(error)) {
        if (!error.response) {
            return "Unable to reach the backend API. Make sure the server is running.";
        }

        const data = error.response.data as ProblemDetailLike | undefined;
        if (data?.detail) return data.detail;
        if (data?.message) return data.message;
    }

    if (error instanceof Error && error.message.trim().length > 0) {
        return error.message;
    }

    return fallback;
}

function bytesToBase64(bytes: Uint8Array) {
    const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
    return globalThis.btoa(binary);
}

function buildPayload(input: CastVoteInput) {
    return {
        ciphertext: bytesToBase64(input.ciphertext),
        nullifier: input.nullifier.trim(),
        proof: input.proof.map((value) => value.toString()),
    };
}

export async function castMyVote(
    electionPublicId: string,
    input: CastVoteInput
): Promise<CastVoteReceipt> {
    const { data } = await api.post<CastVoteReceipt>(
        `${ELECTIONS_ENDPOINT}/${electionPublicId}/votes/me`,
        buildPayload(input)
    );
    return data;
}

export const voteManagement = {
    castMyVote,
    asErrorMessage,
} as const;
