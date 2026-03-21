export type CandidateStatus = "PENDING_APPROVAL" | "ACTIVE" | "WITHDRAWN" | "DISQUALIFIED";

export type Candidate = {
    publicId: string;
    electionPublicId: string;
    status: CandidateStatus;
    fullName: string;
    partyPublicId?: string | null;
    partyName?: string | null;
};

export type CreateCandidateByCinInput = {
    citizenCin: string;
    partyPublicId?: string;
    status?: CandidateStatus;
};
