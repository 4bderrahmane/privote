package org.privote.backend.exception;

import jakarta.ws.rs.WebApplicationException;
import lombok.Getter;

@Getter
public class KeycloakAdminException extends RuntimeException
{
    private final int status;
    private final String userId;

    private KeycloakAdminException(String message, int status, String userId, Throwable cause)
    {
        super(message, cause);
        this.status = status;
        this.userId = userId;
    }

    public static KeycloakAdminException from(WebApplicationException e, String userId, String msg)
    {
        int st = e.getResponse() != null ? e.getResponse().getStatus() : 500;
        return new KeycloakAdminException(msg, st, userId, e);
    }

    public int status()
    {
        return status;
    }
}
