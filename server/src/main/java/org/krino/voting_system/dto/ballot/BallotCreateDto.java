package org.krino.voting_system.dto.ballot;

import lombok.Data;

import java.util.UUID;

@Data
public class BallotCreateDto
{
    private UUID electionPublicId;
    private byte[] ciphertext;
    private String ciphertextHash;
    private String nullifier;
    private String zkProof;
    private String transactionHash;
    private Long blockNumber;
    private Long candidateId;
}
