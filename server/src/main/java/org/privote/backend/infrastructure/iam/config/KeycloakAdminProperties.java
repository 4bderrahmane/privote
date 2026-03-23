package org.privote.backend.infrastructure.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak-admin")
public record KeycloakAdminProperties(
        String serverUrl,
        String realm,
        String clientId,
        String clientSecret
)
{
}