package org.privote.backend.dto.candidate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.privote.backend.entity.enums.CandidateStatus;

import java.util.UUID;

@Data
public class CandidateCreateDto
{
    @NotNull(message = "citizenPublicId is required")
    private UUID citizenPublicId;

    @NotNull(message = "electionPublicId is required")
    private UUID electionPublicId;

    private UUID partyPublicId;

    private CandidateStatus status;
}
