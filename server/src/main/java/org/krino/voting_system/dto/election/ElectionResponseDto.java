package org.krino.voting_system.dto.election;

import org.krino.voting_system.entity.Election;
import org.krino.voting_system.entity.enums.ElectionPhase;

import java.time.Instant;
import java.util.UUID;

public record ElectionResponseDto(
        UUID publicId,
        String title,
        String description,
        Instant startTime,
        Instant endTime,
        ElectionPhase phase,
        String externalNullifier,
        String contractAddress,
        byte[] encryptionPublicKey,
        Instant createdAt,
        Instant updatedAt
)
{
    public static ElectionResponseDto fromEntity(Election election)
    {
        return new ElectionResponseDto(
                election.getPublicId(),
                election.getTitle(),
                election.getDescription(),
                election.getStartTime(),
                election.getEndTime(),
                election.getPhase(),
                election.getExternalNullifier() == null ? null : election.getExternalNullifier().toString(),
                election.getContractAddress(),
                election.getEncryptionPublicKey(),
                election.getCreatedAt(),
                election.getUpdatedAt()
        );
    }
}
