export type CastVoteInput = {
    ciphertext: Uint8Array;
    nullifier: string;
    proof: Array<string | bigint | number>;
};

export type CastVoteReceipt = {
    ballotId: string;
    electionPublicId: string;
    ciphertextHash: string;
    nullifier: string;
    transactionHash: string;
    blockNumber?: number | null;
    castAt?: string | null;
};

export type VoteRecord = {
    id: string;
    electionId: string;
    electionName: string;
    votedAt?: string | null;
    transactionHash?: string | null;
    ciphertextHash?: string | null;
    nullifier?: string | null;
    blockNumber?: number | null;
    status: "confirmed" | "recorded";
    receiptAvailable: boolean;
};
