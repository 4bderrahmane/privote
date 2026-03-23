package org.privote.backend.service.account;

import lombok.RequiredArgsConstructor;
import org.privote.backend.dto.citizen.CitizenResponseDto;
import org.privote.backend.dto.citizen.CitizenSelfUpdateRequest;
import org.privote.backend.entity.Citizen;
import org.privote.backend.infrastructure.iam.KeycloakAdminGateway;
import org.privote.backend.service.CitizenService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountProfileService
{
    private final CitizenService citizenService;
    private final KeycloakAdminGateway keycloak;

    public CitizenResponseDto getMyProfile(String userId)
    {
        return CitizenResponseDto.fromEntity(citizenByUserId(userId));
    }

    public CitizenResponseDto updateMyProfile(String userId, CitizenSelfUpdateRequest request)
    {
        UUID citizenId = parseUserId(userId);

        keycloak.updateUserProfile(userId, request);
        Citizen updatedCitizen = citizenService.updateOwnProfile(citizenId, request);

        return CitizenResponseDto.fromEntity(updatedCitizen);
    }

    private Citizen citizenByUserId(String userId)
    {
        return citizenService.getCitizenByUUID(parseUserId(userId));
    }

    private UUID parseUserId(String userId)
    {
        return UUID.fromString(userId);
    }
}
