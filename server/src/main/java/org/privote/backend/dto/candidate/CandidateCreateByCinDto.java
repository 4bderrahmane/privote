package org.privote.backend.dto.candidate;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.privote.backend.entity.enums.CandidateStatus;

import java.util.UUID;

@Data
public class CandidateCreateByCinDto
{
    @NotBlank(message = "citizenCin is required")
    private String citizenCin;
    private UUID partyPublicId;
    private CandidateStatus status;
}
