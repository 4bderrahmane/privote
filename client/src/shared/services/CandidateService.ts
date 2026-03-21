import axios from "axios";
import api from "@/api.ts";
import type { Candidate, CreateCandidateByCinInput } from "../types/candidate";

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

    return fallback;
}

function buildCreatePayload(input: CreateCandidateByCinInput) {
    return {
        citizenCin: input.citizenCin.trim(),
        partyPublicId: input.partyPublicId || undefined,
        status: input.status || undefined,
    };
}

export async function getCandidatesByElectionPublicId(electionPublicId: string): Promise<Candidate[]> {
    const { data } = await api.get<Candidate[]>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/candidates`);
    return data;
}

export async function getActiveCandidatesByElectionPublicId(electionPublicId: string): Promise<Candidate[]> {
    const { data } = await api.get<Candidate[]>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/candidates/active`);
    return data;
}

export async function createCandidateForElection(
    electionPublicId: string,
    input: CreateCandidateByCinInput
): Promise<Candidate> {
    const { data } = await api.post<Candidate>(
        `${ELECTIONS_ENDPOINT}/${electionPublicId}/candidates`,
        buildCreatePayload(input)
    );
    return data;
}

export const candidateManagement = {
    getCandidatesByElectionPublicId,
    getActiveCandidatesByElectionPublicId,
    createCandidateForElection,
    asErrorMessage,
} as const;
