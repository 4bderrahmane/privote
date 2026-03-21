import axios from "axios";
import api from "@/api.ts";
import type { VoterRegistration } from "../types/voterRegistration";

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

export async function getMyRegistrationByElectionPublicId(
    electionPublicId: string
): Promise<VoterRegistration | null> {
    try {
        const { data } = await api.get<VoterRegistration>(
            `${ELECTIONS_ENDPOINT}/${electionPublicId}/registrations/me`
        );
        return data;
    } catch (error) {
        if (axios.isAxiosError(error) && error.response?.status === 404) {
            return null;
        }
        throw error;
    }
}

export async function registerMyCommitment(
    electionPublicId: string,
    identityCommitment: string
): Promise<VoterRegistration> {
    const { data } = await api.post<VoterRegistration>(
        `${ELECTIONS_ENDPOINT}/${electionPublicId}/registrations/me`,
        { identityCommitment }
    );
    return data;
}

export const voterRegistrationManagement = {
    getMyRegistrationByElectionPublicId,
    registerMyCommitment,
    asErrorMessage,
} as const;
