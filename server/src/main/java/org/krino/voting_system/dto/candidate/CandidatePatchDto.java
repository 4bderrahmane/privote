package org.krino.voting_system.dto.candidate;

import lombok.Data;
import org.krino.voting_system.entity.enums.CandidateStatus;

import java.util.UUID;

@Data
public class CandidatePatchDto
{
    private UUID citizenPublicId;

    private UUID electionPublicId;

    private UUID partyPublicId;

    private CandidateStatus status;
}
