package org.privote.backend.infrastructure.iam;

import org.privote.backend.dto.citizen.CitizenSelfUpdateRequest;

public interface KeycloakAdminGateway
{
    void updateUserProfile(String userId, CitizenSelfUpdateRequest request);

    void disableUser(String userId);

    void logoutUser(String userId);

    void deleteUser(String userId);
}
