package org.krino.voting_system.service.account;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.dto.citizen.CitizenResponseDto;
import org.krino.voting_system.dto.citizen.CitizenSelfUpdateRequest;
import org.krino.voting_system.entity.Citizen;
import org.krino.voting_system.infrastructure.iam.KeycloakAdminGateway;
import org.krino.voting_system.service.CitizenService;
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
