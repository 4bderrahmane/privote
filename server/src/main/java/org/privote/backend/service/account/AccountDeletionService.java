package org.privote.backend.service.account;

import lombok.RequiredArgsConstructor;
import org.privote.backend.infrastructure.iam.KeycloakAdminGateway;
import org.privote.backend.service.CitizenService;
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
