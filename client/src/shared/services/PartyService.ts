import axios from "axios";
import api from "@/api.ts";
import type { CreatePartyInput, Party } from "../types/party";

const PARTIES_ENDPOINT = "/parties";

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

export async function getAllParties(): Promise<Party[]> {
    const { data } = await api.get<Party[]>(PARTIES_ENDPOINT);
    return data;
}

export async function createParty(input: CreatePartyInput): Promise<Party> {
    const payload = {
        name: input.name.trim(),
        description: input.description?.trim() || undefined,
        memberCins: input.memberCins.map((cin) => cin.trim()).filter((cin) => cin.length > 0),
    };
    const { data } = await api.post<Party>(`${PARTIES_ENDPOINT}/create`, payload);
    return data;
}

export const partyManagement = {
    getAllParties,
    createParty,
    asErrorMessage,
} as const;
