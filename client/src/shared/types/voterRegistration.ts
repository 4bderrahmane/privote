export type ParticipationStatus = "REGISTERED" | "ELIGIBLE" | "CAST";

export type CommitmentStatus = "PENDING" | "ON_CHAIN" | "FAILED";

export type VoterRegistration = {
    electionPublicId: string;
    citizenKeycloakId: string;
    participationStatus: ParticipationStatus;
    commitmentStatus: CommitmentStatus;
    identityCommitment: string;
    merkleLeafIndex?: number | null;
    transactionHash?: string | null;
    registeredAt?: string | null;
};

