export type ElectionResultCandidate = {
    candidatePublicId: string;
    fullName: string;
    partyName?: string | null;
    votes: number;
    percentage: number;
};

export type ElectionResult = {
    electionPublicId: string;
    electionTitle: string;
    endTime?: string | null;
    published: boolean;
    totalVotes: number;
    talliedBallots: number;
    registeredVoters: number;
    turnoutPercentage: number;
    candidates: ElectionResultCandidate[];
};

export type TallyBallot = {
    ballotId: string;
    ciphertext: string;
    ciphertextHash?: string | null;
    transactionHash?: string | null;
    blockNumber?: number | null;
    castAt?: string | null;
    candidatePublicId?: string | null;
};

export type PublishElectionResultsInput = {
    assignments: Array<{
        ballotId: string;
        candidatePublicId: string;
    }>;
};
