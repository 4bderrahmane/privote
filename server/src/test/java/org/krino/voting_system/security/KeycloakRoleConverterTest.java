package org.krino.voting_system.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakRoleConverterTest
{
    @Test
    void mapsRealmAndConfiguredClientRoles()
    {
        KeycloakRoleConverter converter = new KeycloakRoleConverter("voting-backend");
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "7d0ef071-8206-418a-a5a7-c49ad8b7f2ce",
                "preferred_username", "4bderrahmane",
                "realm_access", Map.of("roles", List.of("admin")),
                "resource_access", Map.of(
                        "voting-backend", Map.of("roles", List.of("election-manager"))
                )
        ));

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals("4bderrahmane", authentication.getName());
        assertEquals(
                Set.of("ROLE_ADMIN", "ROLE_election-manager"),
                authorityNames(authentication)
        );
    }

    @Test
    void ignoresRolesForOtherClients()
    {
        KeycloakRoleConverter converter = new KeycloakRoleConverter("voting-backend");
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "0f43f827-320a-4f23-a28f-548ab4dc0930",
                "resource_access", Map.of(
                        "some-other-client", Map.of("roles", List.of("admin"))
                )
        ));

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertTrue(authentication.getAuthorities().isEmpty());
    }

    @Test
    void keepsWorkingWhenClientRoleSourceIsBlank()
    {
        KeycloakRoleConverter converter = new KeycloakRoleConverter("   ");
        Jwt jwt = jwtWithClaims(Map.of(
                "sub", "61065b44-a9f4-4f0b-a10e-e8ab5793ba6e",
                "realm_access", Map.of("roles", List.of("admin")),
                "resource_access", Map.of(
                        "voting-backend", Map.of("roles", List.of("ignored"))
                )
        ));

        JwtAuthenticationToken authentication = (JwtAuthenticationToken) converter.convert(jwt);

        assertEquals(Set.of("ROLE_ADMIN"), authorityNames(authentication));
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims)
    {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none");

        claims.forEach(builder::claim);
        return builder.build();
    }

    private static Set<String> authorityNames(JwtAuthenticationToken authentication)
    {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }
}
