package org.privote.backend.dto.result;

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
