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

