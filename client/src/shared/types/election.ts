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

export type ElectionSummary = {
    id: string;
    title: string;
    description?: string;
    startsAt: string; // ISO
    endsAt: string; // ISO
    phase: ElectionPhase;
    contractAddress?: string | null;
    encryptionPublicKey?: string | null;
    eligibleVoters?: number;
    candidates?: number;
    hasVoted?: boolean;
};

export type CreateElectionForm = {
    title: string;
    description: string;
    startDate: string;
    startClock: string;
    endDate: string;
    endClock: string;
    vaultPassword: string;
    confirmVaultPassword: string;
};

export const INITIAL_FORM: CreateElectionForm = {
    title: "",
    description: "",
    startDate: "",
    startClock: "09:00",
    endDate: "",
    endClock: "18:00",
    vaultPassword: "",
    confirmVaultPassword: "",
};

export type PreparedElection = Election & {
    startsAtIso: string;
    startsAtMs: number;
    endsAtMs: number;
    updatedAtMs: number;
    status: ElectionStatus;
    action: DashboardAction;
};

export type DashboardAction = "deploy" | "start" | "completeVoting" | null;

// export type PreparedElection = Election & {
//     startsAtIso: string;
//     startsAtMs: number;
//     endsAtMs: number;
//     status: ElectionStatus;
// };
