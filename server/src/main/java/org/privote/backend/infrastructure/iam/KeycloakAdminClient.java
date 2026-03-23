package org.privote.backend.infrastructure.iam;

import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.privote.backend.dto.citizen.CitizenSelfUpdateRequest;
import org.privote.backend.exception.KeycloakAdminException;
import org.privote.backend.infrastructure.iam.config.KeycloakAdminProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class KeycloakAdminClient implements KeycloakAdminGateway
{
    private final Keycloak keycloak;
    private final KeycloakAdminProperties props;

    @Override
    public void updateUserProfile(String userId, CitizenSelfUpdateRequest request)
    {
        try
        {
            var userResource = keycloak.realm(props.realm())
                    .users()
                    .get(userId);

            UserRepresentation rep = userResource.toRepresentation();

            if (request.getFirstName() != null)
            {
                rep.setFirstName(request.getFirstName().trim());
            }
            if (request.getLastName() != null)
            {
                rep.setLastName(request.getLastName().trim());
            }
            if (request.getEmail() != null)
            {
                String normalizedEmail = request.getEmail().trim();
                if (!normalizedEmail.equalsIgnoreCase(rep.getEmail()))
                {
                    rep.setEmailVerified(false);
                }
                rep.setEmail(normalizedEmail);
            }

            Map<String, List<String>> attributes = rep.getAttributes() == null
                    ? new HashMap<>()
                    : new HashMap<>(rep.getAttributes());

            putAttribute(attributes, "phone_number", request.getPhoneNumber());
            putAttribute(attributes, "address", request.getAddress());
            putAttribute(attributes, "region", request.getRegion());
            putAttribute(attributes, "birthplace", request.getBirthPlace());
            putAttribute(attributes, "birthdate", request.getBirthDate() == null ? null : request.getBirthDate().toString());

            rep.setAttributes(attributes);
            userResource.update(rep);
        } catch (WebApplicationException e)
        {
            throw KeycloakAdminException.from(e, userId, String.format("Failed to update user with id: %s in Keycloak", userId));
        }
    }

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

    private void putAttribute(Map<String, List<String>> attributes, String key, String value)
    {
        if (value == null) return;

        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty())
        {
            attributes.remove(key);
            return;
        }

        attributes.put(key, new ArrayList<>(List.of(normalizedValue)));
    }
}