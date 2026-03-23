package org.privote.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticatedActorResolverTest
{
    private final AuthenticatedActorResolver resolver = new AuthenticatedActorResolver();

    @Test
    void resolvesActorIdFromJwtSubject()
    {
        UUID userId = UUID.randomUUID();
        Jwt jwt = jwtWithClaims(Map.of("sub", userId.toString()));

        assertEquals(userId, resolver.actorId(jwt));
        assertEquals(userId.toString(), resolver.subject(jwt));
    }

    @Test
    void rejectsJwtWithoutSubject()
    {
        Jwt jwt = jwtWithClaims(Map.of("preferred_username", "test-user"));

        assertThrows(InsufficientAuthenticationException.class, () -> resolver.actorId(jwt));
    }

    @Test
    void rejectsNonUuidSubject()
    {
        Jwt jwt = jwtWithClaims(Map.of("sub", "not-a-uuid"));

        assertThrows(BadCredentialsException.class, () -> resolver.actorId(jwt));
    }

    private static Jwt jwtWithClaims(Map<String, Object> claims)
    {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none");

        claims.forEach(builder::claim);
        return builder.build();
    }
}
