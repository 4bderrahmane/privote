import axios from "axios";
import api from "@/api.ts";
import type { UserResponseDTO, UserUpdateDTO } from "../types/types.ts";

const MY_PROFILE_ENDPOINT = "/users/me";

type ProblemDetailLike = {
    detail?: string;
    message?: string;
};

function asErrorMessage(error: unknown, fallback: string) {
    if (axios.isAxiosError(error)) {
        if (!error.response) {
            return "Unable to reach the backend API. Make sure the server is running on port 9090.";
        }

        if (typeof error.response.data === "string" && error.response.data.includes("ECONNREFUSED")) {
            return "Unable to reach the backend API. Make sure the server is running on port 9090.";
        }

        const data = error.response.data as ProblemDetailLike | undefined;
        if (data?.detail) return data.detail;
        if (data?.message) return data.message;
    }
    return fallback;
}

export async function getCurrentUser(): Promise<UserResponseDTO> {
    const { data } = await api.get<UserResponseDTO>(MY_PROFILE_ENDPOINT);
    return data;
}

export async function updatePartialProfile(updateData: Partial<UserUpdateDTO>): Promise<UserResponseDTO> {
    const { data } = await api.patch<UserResponseDTO>(MY_PROFILE_ENDPOINT, updateData);
    return data;
}

export async function deleteAccount(): Promise<void> {
    await api.delete(MY_PROFILE_ENDPOINT);
}

export const userManagement = {
    getCurrentUser,
    updatePartialProfile,
    deleteAccount,
    asErrorMessage,
} as const;
