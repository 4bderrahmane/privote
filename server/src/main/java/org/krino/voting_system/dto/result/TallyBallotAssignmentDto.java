package org.krino.voting_system.dto.result;

import lombok.Data;

import java.util.UUID;

@Data
public class TallyBallotAssignmentDto
{
    private UUID ballotId;
    private UUID candidatePublicId;
}
