package org.privote.backend.utilities;

import lombok.Getter;
import org.springframework.http.HttpStatus;


@Getter
public enum ErrorCode
{

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN),

    INSUFFICIENT_FUNDS(HttpStatus.CONFLICT),
    DATA_CONFLICT(HttpStatus.CONFLICT),
    OPERATION_NOT_ALLOWED(HttpStatus.CONFLICT), // business-rule / invalid state (previously 405)

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    EXTERNAL_SERVICE_FAILURE(HttpStatus.BAD_GATEWAY),   // upstream error response / failure
    TIMEOUT_OCCURRED(HttpStatus.GATEWAY_TIMEOUT);       // upstream timeout

    private final HttpStatus status;

    ErrorCode(HttpStatus status)
    {
        this.status = status;
    }

    public int httpStatus()
    {
        return status.value();
    }
}

/*
 * ErrorCode is an application-level "reason" that maps to an HTTP status.
 * <p>
 * How to add a new error code (mapping rule):
 * - Use 400 (BAD_REQUEST) for syntactically invalid input (missing/invalid fields, parse errors).
 * - Use 401 (UNAUTHORIZED) when the user is not authenticated (no/invalid token).
 * - Use 403 (FORBIDDEN) when authenticated but not allowed (role/permission/account locked).
 * - Use 404 (NOT_FOUND) when the requested resource does not exist.
 * - Use 409 (CONFLICT) when the request is valid but conflicts with current state/data (duplicate, invalid state transitions).
 * - Use 422 (UNPROCESSABLE_ENTITY) when input is syntactically valid but semantically invalid (optional, if you want that distinction).
 * - Use 502 (BAD_GATEWAY) / 503 (SERVICE_UNAVAILABLE) when an upstream service (Keycloak, external APIs) fails.
 * - Use 504 (GATEWAY_TIMEOUT) when an upstream service times out.
 * - Use 500 (INTERNAL_SERVER_ERROR) for unexpected server-side failures.
 * <p>
 * IMPORTANT: Don't use 405 (METHOD_NOT_ALLOWED) for business rules; it is reserved for HTTP method mismatch.
 */