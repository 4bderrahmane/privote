package org.privote.backend.dto.ballot;

import org.privote.backend.entity.Ballot;

import java.time.Instant;
import java.util.UUID;

public record BallotCastResponseDto(
        UUID ballotId,
        UUID electionPublicId,
        String ciphertextHash,
        String nullifier,
        String transactionHash,
        Long blockNumber,
        Instant castAt
)
{
    public static BallotCastResponseDto fromEntity(Ballot ballot)
    {
        return new BallotCastResponseDto(
                ballot.getId(),
                ballot.getElection().getPublicId(),
                ballot.getCiphertextHash(),
                ballot.getNullifier(),
                ballot.getTransactionHash(),
                ballot.getBlockNumber(),
                ballot.getCastAt()
        );
    }
}
