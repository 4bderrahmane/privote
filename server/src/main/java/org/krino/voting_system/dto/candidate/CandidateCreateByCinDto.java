package org.krino.voting_system.dto.candidate;

import lombok.Data;
import org.krino.voting_system.entity.enums.CandidateStatus;

import java.util.UUID;

@Data
public class CandidateCreateByCinDto
{
    private String citizenCin;
    private UUID partyPublicId;
    private CandidateStatus status;
}
