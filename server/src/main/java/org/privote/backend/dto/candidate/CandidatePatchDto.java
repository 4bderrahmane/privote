package org.privote.backend.dto.candidate;

import lombok.Data;
import org.privote.backend.entity.enums.CandidateStatus;

import java.util.UUID;

@Data
public class CandidatePatchDto
{
    private UUID citizenPublicId;

    private UUID electionPublicId;

    private UUID partyPublicId;

    private CandidateStatus status;
}
