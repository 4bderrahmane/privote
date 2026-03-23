package org.privote.backend.infrastructure.iam.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.privote.backend.infrastructure.iam.KeycloakAdminClient;
import org.privote.backend.infrastructure.iam.KeycloakAdminGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakAdminConfiguration
{

    @Bean(destroyMethod = "close")
    Keycloak keycloakAdminClient(KeycloakAdminProperties props)
    {
        return KeycloakBuilder.builder()
                .serverUrl(props.serverUrl())
                .realm(props.realm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(props.clientId())
                .clientSecret(props.clientSecret())
                .build();
    }

    @Bean
    KeycloakAdminGateway keycloakAdminGateway(Keycloak keycloak, KeycloakAdminProperties props)
    {
        return new KeycloakAdminClient(keycloak, props);
    }
}