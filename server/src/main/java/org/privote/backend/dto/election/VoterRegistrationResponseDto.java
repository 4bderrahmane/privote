package org.privote.backend.dto.election;

import org.privote.backend.entity.enums.CommitmentStatus;
import org.privote.backend.entity.enums.ParticipationStatus;

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
