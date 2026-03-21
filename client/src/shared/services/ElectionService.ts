import axios from "axios";
import api from "@/api.ts";
import type { CreateElectionInput, Election } from "../types/election";

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

function normalizeHex(value: string) {
    return value.trim().replace(/^0x/i, "").replace(/\s+/g, "");
}

function hexToBase64(hex: string) {
    const normalized = normalizeHex(hex);
    if (!/^[0-9a-fA-F]*$/.test(normalized)) {
        throw new Error("Keys must be provided as hexadecimal strings.");
    }
    if (normalized.length % 2 !== 0) {
        throw new Error("Hexadecimal strings must contain an even number of characters.");
    }

    const bytes = new Uint8Array(normalized.length / 2);
    for (let index = 0; index < bytes.length; index += 1) {
        bytes[index] = Number.parseInt(normalized.slice(index * 2, index * 2 + 2), 16);
    }

    const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
    return globalThis.btoa(binary);
}

function base64ToBytes(value: string) {
    const binary = globalThis.atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
}

function bytesToBase64(bytes: Uint8Array) {
    const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join("");
    return globalThis.btoa(binary);
}

function buildCreatePayload(input: CreateElectionInput) {
    return {
        title: input.title.trim(),
        description: input.description?.trim() || undefined,
        startTime: input.startTime || undefined,
        endTime: input.endTime,
        phase: input.phase || undefined,
        externalNullifier: input.externalNullifier?.trim() || undefined,
        coordinatorKeycloakId: input.coordinatorKeycloakId,
        encryptionPublicKey: hexToBase64(input.encryptionPublicKey),
    };
}

function buildEndPayload(decryptionMaterialPkcs8: Uint8Array) {
    return {
        decryptionMaterial: bytesToBase64(decryptionMaterialPkcs8),
    };
}

export async function getAllElections(): Promise<Election[]> {
    const { data } = await api.get<Election[]>(ELECTIONS_ENDPOINT);
    return data;
}

export async function getElectionByPublicId(electionPublicId: string): Promise<Election> {
    const { data } = await api.get<Election>(`${ELECTIONS_ENDPOINT}/${electionPublicId}`);
    return data;
}

export async function createElection(input: CreateElectionInput): Promise<Election> {
    const { data } = await api.post<Election>(`${ELECTIONS_ENDPOINT}/create`, buildCreatePayload(input));
    return data;
}

export async function deployElection(electionPublicId: string): Promise<Election> {
    const { data } = await api.post<Election>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/deploy`);
    return data;
}

export async function startElection(electionPublicId: string): Promise<Election> {
    const { data } = await api.post<Election>(`${ELECTIONS_ENDPOINT}/${electionPublicId}/start`);
    return data;
}

export async function endElection(
    electionPublicId: string,
    decryptionMaterialPkcs8: Uint8Array
): Promise<Election> {
    const { data } = await api.post<Election>(
        `${ELECTIONS_ENDPOINT}/${electionPublicId}/end`,
        buildEndPayload(decryptionMaterialPkcs8)
    );
    return data;
}

export function decodeBase64Key(value: string): Uint8Array {
    return base64ToBytes(value);
}

export const electionManagement = {
    getAllElections,
    getElectionByPublicId,
    createElection,
    deployElection,
    startElection,
    endElection,
    decodeBase64Key,
    asErrorMessage,
} as const;
