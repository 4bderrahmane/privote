package org.krino.voting_system.infrastructure.iam;

import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.krino.voting_system.exception.KeycloakAdminException;
import org.krino.voting_system.infrastructure.iam.config.KeycloakAdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
public class KeycloakAdminClient implements KeycloakAdminGateway
{

    private final Logger logger = LoggerFactory.getLogger(KeycloakAdminClient.class);
    private final Keycloak keycloak;
    private final KeycloakAdminProperties props;

    @Override
    public void deleteUser(String userId)
    {
        try
        {
            keycloak.realm(props.realm())
                    .users()
                    .delete(userId);
        } catch (WebApplicationException e)
        {
            if (e.getResponse() != null && e.getResponse().getStatus() == 404)
            {
                return;
            }
            throw KeycloakAdminException.from(e, userId, String.format("Failed to delete user with id: %s in Keycloak", userId));
        }
    }

    @Override
    public void disableUser(String userId)
    {
        try
        {
            var users = keycloak
                    .realm(props.realm())
                    .users();

            var rep = users.get(userId).toRepresentation();
            rep.setEnabled(false);
            users.get(userId).update(rep);
        } catch (WebApplicationException e)
        {
            throw KeycloakAdminException.from(e, userId, String.format("Failed to disable user with id: %s in Keycloak", userId));
        }
    }

    @Override
    public void logoutUser(String userId)
    {
        try
        {
            keycloak.realm(props.realm())
                    .users()
                    .get(userId)
                    .logout();
        } catch (WebApplicationException e)
        {
            throw KeycloakAdminException.from(e, userId, String.format("Failed to logout user with id: %s in Keycloak", userId));
        }
    }
}