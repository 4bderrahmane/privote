package org.krino.voting_system.infrastructure.iam;

import org.krino.voting_system.dto.citizen.CitizenSelfUpdateRequest;

public interface KeycloakAdminGateway
{
    void updateUserProfile(String userId, CitizenSelfUpdateRequest request);

    void disableUser(String userId);

    void logoutUser(String userId);

    void deleteUser(String userId);
}
