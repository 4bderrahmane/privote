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

export type CandidateForm = {
    citizenCin: string;
    partyPublicId: string;
    status: CandidateStatus;
};

export const INITIAL_CANDIDATE_FORM: CandidateForm = {
    citizenCin: "",
    partyPublicId: "",
    status: "ACTIVE",
};

export const STATUS_ORDER: Record<CandidateStatus, number> = {
    ACTIVE: 0,
    PENDING_APPROVAL: 1,
    WITHDRAWN: 2,
    DISQUALIFIED: 3,
};
