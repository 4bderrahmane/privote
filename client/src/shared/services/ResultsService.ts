import axios from "axios";
import api from "@/api.ts";
import type { ElectionResult, PublishElectionResultsInput, TallyBallot } from "../types/result";

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

export async function getElectionResults(electionPublicId: string): Promise<ElectionResult> {
    const { data } = await api.get<ElectionResult>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/results`);
    return data;
}

export async function getTallyBallots(electionPublicId: string): Promise<TallyBallot[]> {
    const { data } = await api.get<TallyBallot[]>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/results/ballots`);
    return data;
}

export async function publishElectionResults(
    electionPublicId: string,
    input: PublishElectionResultsInput
): Promise<ElectionResult> {
    const { data } = await api.post<ElectionResult>(
        `${ELECTIONS_ENDPOINT}/${electionPublicId}/results/publish`,
        input
    );
    return data;
}

export const resultsManagement = {
    getElectionResults,
    getTallyBallots,
    publishElectionResults,
    asErrorMessage,
} as const;
