package org.privote.backend.service;

import lombok.RequiredArgsConstructor;
import org.privote.backend.audit.AdminAuditRunner;
import org.privote.backend.dto.candidate.CandidateCreateByCinDto;
import org.privote.backend.dto.candidate.CandidateCreateDto;
import org.privote.backend.dto.candidate.CandidatePatchDto;
import org.privote.backend.dto.candidate.CandidateResponseDto;
import org.privote.backend.entity.enums.SystemLogAction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateAdminService
{
    private static final String TARGET_TYPE = "Candidate";
    private static final String DETAIL_ELECTION_UUID = "electionUuid";
    private static final String DETAIL_STATUS = "status";
    private static final String DETAIL_REASON = "reason";

    private final CandidateService candidateService;
    private final AdminAuditRunner adminAuditRunner;

    public CandidateResponseDto createCandidate(UUID adminKeycloakId, CandidateCreateDto candidate)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.CREATE_CANDIDATE, TARGET_TYPE, null),
                () -> candidateService.createCandidate(candidate),
                result -> result.publicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, result.electionPublicId()),
                        AdminAuditRunner.detail(DETAIL_STATUS, result.status())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, candidate == null ? null : candidate.getElectionPublicId()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public CandidateResponseDto createCandidateByCin(UUID adminKeycloakId, UUID electionPublicId, CandidateCreateByCinDto candidate)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.CREATE_CANDIDATE_BY_CIN, TARGET_TYPE, null),
                () -> candidateService.createCandidateByCin(electionPublicId, candidate),
                result -> result.publicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, result.electionPublicId()),
                        AdminAuditRunner.detail(DETAIL_STATUS, result.status())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, electionPublicId),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public CandidateResponseDto updateCandidate(UUID adminKeycloakId, UUID publicId, CandidateCreateDto candidate)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.UPDATE_CANDIDATE, TARGET_TYPE, publicId.toString()),
                () -> candidateService.updateCandidate(publicId, candidate),
                result -> result.publicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, result.electionPublicId()),
                        AdminAuditRunner.detail(DETAIL_STATUS, result.status())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, candidate == null ? null : candidate.getElectionPublicId()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public CandidateResponseDto patchCandidate(UUID adminKeycloakId, UUID publicId, CandidatePatchDto candidate)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.PATCH_CANDIDATE, TARGET_TYPE, publicId.toString()),
                () -> candidateService.patchCandidate(publicId, candidate),
                result -> result.publicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, result.electionPublicId()),
                        AdminAuditRunner.detail(DETAIL_STATUS, result.status())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_ELECTION_UUID, candidate == null ? null : candidate.getElectionPublicId()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public void deleteCandidate(UUID adminKeycloakId, UUID publicId)
    {
        adminAuditRunner.runVoid(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.DELETE_CANDIDATE, TARGET_TYPE, publicId.toString()),
                () -> candidateService.deleteCandidateByPublicId(publicId),
                () -> null,
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }
}
