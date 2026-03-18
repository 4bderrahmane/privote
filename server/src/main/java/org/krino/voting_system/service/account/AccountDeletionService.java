package org.krino.voting_system.service.account;

import lombok.RequiredArgsConstructor;
import org.krino.voting_system.infrastructure.iam.KeycloakAdminGateway;
import org.krino.voting_system.service.CitizenService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDeletionService
{
    private final CitizenService citizenService;
    private final KeycloakAdminGateway keycloak;

    public void deleteMyAccount(String userId)
    {
        UUID uuid = UUID.fromString(userId);
        citizenService.softDeleteCitizenByUUID(uuid);

        keycloak.disableUser(userId);
        keycloak.logoutUser(userId);
        keycloak.deleteUser(userId);
    }
}
