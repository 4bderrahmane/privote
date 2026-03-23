package org.krino.voting_system.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuthenticatedActorResolver
{
    public UUID actorId(Jwt jwt)
    {
        String subject = subject(jwt);
        try
        {
            return UUID.fromString(subject);
        }
        catch (IllegalArgumentException ex)
        {
            throw new BadCredentialsException("Authenticated subject claim must be a valid UUID", ex);
        }
    }

    public String subject(Jwt jwt)
    {
        if (jwt == null)
        {
            throw new InsufficientAuthenticationException("Authentication principal is required");
        }

        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank())
        {
            throw new InsufficientAuthenticationException("JWT subject claim is required");
        }
        return subject;
    }
}
