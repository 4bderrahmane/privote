package org.krino.voting_system.dto.election;

import org.krino.voting_system.entity.enums.CommitmentStatus;
import org.krino.voting_system.entity.enums.ParticipationStatus;

import java.time.Instant;
import java.util.UUID;

public record VoterRegistrationResponseDto(
        UUID electionPublicId,
        UUID citizenKeycloakId,
        ParticipationStatus participationStatus,
        CommitmentStatus commitmentStatus,
        String identityCommitment,
        Long merkleLeafIndex,
        String transactionHash,
        Instant registeredAt
)
{
}
