package org.krino.voting_system.dto.result;

import java.util.UUID;

public record ElectionResultCandidateDto(
        UUID candidatePublicId,
        String fullName,
        String partyName,
        long votes,
        double percentage
)
{
}
