package org.krino.voting_system.security;

import lombok.AllArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken>
{
    private final String keycloakClientId;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt)
    {
        Collection<GrantedAuthority> authorities = Stream.concat(
                extractRealmRoles(jwt).stream(),
                extractClientRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String name = Optional.ofNullable(jwt.getClaimAsString("preferred_username"))
                .orElse(jwt.getSubject());

        return new JwtAuthenticationToken(jwt, authorities, name);
    }

    private Collection<? extends GrantedAuthority> extractRealmRoles(Jwt jwt)
    {
        Object realmAccessObj = jwt.getClaim("realm_access");
        if (!(realmAccessObj instanceof Map<?, ?> realmAccess)) return Set.of();

        Object rolesObj = realmAccess.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) return Set.of();

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::normalizeRole)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Collection<? extends GrantedAuthority> extractClientRoles(Jwt jwt)
    {
        if (keycloakClientId == null || keycloakClientId.isBlank()) return Set.of();

        Object resourceAccessObj = jwt.getClaim("resource_access");
        if (!(resourceAccessObj instanceof Map<?, ?> resourceAccess)) return Set.of();

        Object clientObj = resourceAccess.get(keycloakClientId);
        if (!(clientObj instanceof Map<?, ?> client)) return Set.of();

        Object rolesObj = client.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) return Set.of();

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(this::normalizeRole)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeRole(String role)
    {
        if ("admin".equalsIgnoreCase(role))
        {
            return "ADMIN";
        }

        return role;
    }

}
