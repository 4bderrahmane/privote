package org.privote.backend.dto.result;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TallyBallotAssignmentDto
{
    @NotNull(message = "ballotId is required")
    private UUID ballotId;

    @NotNull(message = "candidatePublicId is required")
    private UUID candidatePublicId;
}
