package org.privote.backend.service;

import lombok.RequiredArgsConstructor;
import org.privote.backend.audit.AdminAuditRunner;
import org.privote.backend.dto.party.PartyCreateDto;
import org.privote.backend.dto.party.PartyPatchDto;
import org.privote.backend.entity.Party;
import org.privote.backend.entity.enums.SystemLogAction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartyAdminService
{
    private static final String TARGET_TYPE = "Party";
    private static final String DETAIL_NAME = "name";
    private static final String DETAIL_MEMBER_COUNT = "memberCount";
    private static final String DETAIL_REASON = "reason";

    private final PartyService partyService;
    private final AdminAuditRunner adminAuditRunner;

    public Party createParty(UUID adminKeycloakId, PartyCreateDto party)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.CREATE_PARTY, TARGET_TYPE, null),
                () -> partyService.createParty(party),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_NAME, result.getName()),
                        AdminAuditRunner.detail(DETAIL_MEMBER_COUNT, party == null || party.getMemberCins() == null ? null : party.getMemberCins().size())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_NAME, party == null ? null : party.getName()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public Party updateParty(UUID adminKeycloakId, UUID publicId, PartyCreateDto party)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.UPDATE_PARTY, TARGET_TYPE, publicId.toString()),
                () -> partyService.updateParty(publicId, party),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_NAME, result.getName()),
                        AdminAuditRunner.detail(DETAIL_MEMBER_COUNT, party == null || party.getMemberCins() == null ? null : party.getMemberCins().size())
                ),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_NAME, party == null ? null : party.getName()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public Party patchParty(UUID adminKeycloakId, UUID publicId, PartyPatchDto party)
    {
        return adminAuditRunner.run(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.PATCH_PARTY, TARGET_TYPE, publicId.toString()),
                () -> partyService.patchParty(publicId, party),
                result -> result.getPublicId().toString(),
                result -> AdminAuditRunner.detail(DETAIL_NAME, result.getName()),
                ex -> AdminAuditRunner.joinDetails(
                        AdminAuditRunner.detail(DETAIL_NAME, party == null ? null : party.getName()),
                        AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
                )
        );
    }

    public void deleteParty(UUID adminKeycloakId, UUID publicId)
    {
        adminAuditRunner.runVoid(
                AdminAuditRunner.context(adminKeycloakId, SystemLogAction.DELETE_PARTY, TARGET_TYPE, publicId.toString()),
                () -> partyService.deletePartyByPublicId(publicId),
                () -> null,
                ex -> AdminAuditRunner.detail(DETAIL_REASON, AdminAuditRunner.summarizeException(ex))
        );
    }
}
