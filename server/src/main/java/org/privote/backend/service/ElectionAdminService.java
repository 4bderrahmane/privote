package org.privote.backend.service;

import lombok.RequiredArgsConstructor;
import org.privote.backend.audit.AdminAuditRunner;
import org.privote.backend.dto.election.ElectionCreateDto;
import org.privote.backend.dto.election.ElectionEndRequestDto;
import org.privote.backend.dto.election.ElectionPatchDto;
import org.privote.backend.entity.Election;
import org.privote.backend.entity.enums.SystemLogAction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ElectionAdminService
{
    private static final String TARGET_TYPE = "Election";
    private static final String DETAIL_TITLE = "title";
    private static final String DETAIL_PHASE = "phase";
    private static final String DETAIL_CONTRACT_ADDRESS = "contractAddress";
    private static final String DETAIL_REASON = "reason";

    private final ElectionService electionService;
    private final ElectionLifecycleService electionLifecycleService;
    private final AdminAuditRunner adminAuditRunner;

    public Election createElection(UUID adminKeycloakId, ElectionCreateDto election)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.CREATE_ELECTION, TARGET_TYPE, null),
                () -> electionService.createElection(election),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, result.getTitle()),
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, election == null ? null : election.getTitle()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public Election updateElection(UUID adminKeycloakId, UUID publicId, ElectionCreateDto election)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.UPDATE_ELECTION, TARGET_TYPE, publicId.toString()),
                () -> electionService.updateElection(publicId, election),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, result.getTitle()),
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, election == null ? null : election.getTitle()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public Election patchElection(UUID adminKeycloakId, UUID publicId, ElectionPatchDto patch)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.PATCH_ELECTION, TARGET_TYPE, publicId.toString()),
                () -> electionService.patchElection(publicId, patch),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, result.getTitle()),
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_TITLE, patch == null ? null : patch.getTitle()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public void deleteElection(UUID adminKeycloakId, UUID publicId)
    {
        adminAuditRunner.runVoid(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.DELETE_ELECTION, TARGET_TYPE, publicId.toString()),
                () -> electionService.deleteElectionByPublicId(publicId),
                () -> null,
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }

    public Election deployElection(UUID adminKeycloakId, UUID publicId)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.DEPLOY_ELECTION, TARGET_TYPE, publicId.toString()),
                () -> electionLifecycleService.deployElection(publicId),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase()),
                        AdminAuditRunner.detail(DETAIL_CONTRACT_ADDRESS, result.getContractAddress())
                ),
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }

    public Election startElection(UUID adminKeycloakId, UUID publicId)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.START_ELECTION, TARGET_TYPE, publicId.toString()),
                () -> electionLifecycleService.startElection(publicId),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase()),
                        AdminAuditRunner.detail(DETAIL_CONTRACT_ADDRESS, result.getContractAddress())
                ),
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }

    public Election endElection(UUID adminKeycloakId, UUID publicId, ElectionEndRequestDto request)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.END_ELECTION, TARGET_TYPE, publicId.toString()),
                () ->
                {
                    if (request == null || request.getDecryptionMaterial() == null || request.getDecryptionMaterial().length == 0)
                    {
                        throw new IllegalArgumentException("decryptionMaterial is required");
                    }

                    return electionLifecycleService.endElection(publicId, request.getDecryptionMaterial());
                },
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_PHASE, result.getPhase()),
                        AdminAuditRunner.detail(DETAIL_CONTRACT_ADDRESS, result.getContractAddress())
                ),
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }
}
