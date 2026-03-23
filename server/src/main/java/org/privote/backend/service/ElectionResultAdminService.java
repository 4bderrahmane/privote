package org.privote.backend.service;

import lombok.RequiredArgsConstructor;
import org.privote.backend.audit.AdminAuditRunner;
import org.privote.backend.dto.result.ElectionResultResponseDto;
import org.privote.backend.dto.result.PublishElectionResultsRequestDto;
import org.privote.backend.entity.enums.SystemLogAction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ElectionResultAdminService
{
    private static final String TARGET_TYPE = "Election";
    private static final String DETAIL_PUBLISHED = "published";
    private static final String DETAIL_TOTAL_VOTES = "totalVotes";
    private static final String DETAIL_TALLIED_BALLOTS = "talliedBallots";
    private static final String DETAIL_REASON = "reason";

    private final ElectionResultService electionResultService;
    private final AdminAuditRunner adminAuditRunner;

    public ElectionResultResponseDto publishResults(
            UUID adminKeycloakId,
            UUID electionPublicId,
            PublishElectionResultsRequestDto request
    )
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.PUBLISH_ELECTION_RESULTS, TARGET_TYPE, electionPublicId.toString()),
                () -> electionResultService.publishElectionResults(electionPublicId, request),
                result -> result.electionPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_PUBLISHED, result.published()),
                        AdminAuditRunner.detail(DETAIL_TOTAL_VOTES, result.totalVotes()),
                        AdminAuditRunner.detail(DETAIL_TALLIED_BALLOTS, result.talliedBallots())
                ),
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }
}
