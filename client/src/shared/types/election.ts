export type ElectionStatus = "upcoming" | "open" | "closed";

export type ElectionPhase = "REGISTRATION" | "VOTING" | "TALLY";

export type Election = {
    publicId: string;
    title: string;
    description?: string | null;
    startTime?: string | null;
    endTime: string;
    phase: ElectionPhase;
    externalNullifier?: string | null;
    contractAddress?: string | null;
    encryptionPublicKey?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
};

export type CreateElectionInput = {
    title: string;
    description?: string;
    startTime?: string;
    endTime: string;
    phase?: ElectionPhase;
    externalNullifier?: string;
    coordinatorKeycloakId: string;
    encryptionPublicKey: string;
    decryptionKey?: string;
};
