package org.krino.voting_system.dto.ballot;

import org.krino.voting_system.entity.Ballot;

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
