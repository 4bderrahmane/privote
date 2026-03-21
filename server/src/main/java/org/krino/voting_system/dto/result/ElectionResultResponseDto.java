package org.krino.voting_system.dto.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ElectionResultResponseDto(
        UUID electionPublicId,
        String electionTitle,
        Instant endTime,
        boolean published,
        long totalVotes,
        long talliedBallots,
        long registeredVoters,
        double turnoutPercentage,
        List<ElectionResultCandidateDto> candidates
)
{
}
