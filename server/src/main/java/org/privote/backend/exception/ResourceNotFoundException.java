package org.privote.backend.exception;

import lombok.Getter;
import org.privote.backend.utilities.ErrorCode;

import java.util.Objects;

@Getter
public class ResourceNotFoundException extends RuntimeException
{

    private final ErrorCode errorCode;
    private final String resource;
    private final String field;
    private final Object value;

    public ResourceNotFoundException(String resource)
    {
        this(ErrorCode.RESOURCE_NOT_FOUND, resource, "id", null);
    }

    public ResourceNotFoundException(String resource, Object id)
    {
        this(ErrorCode.RESOURCE_NOT_FOUND, resource, "id", id);
    }

    public ResourceNotFoundException(String resource, String field, Object value)
    {
        this(ErrorCode.RESOURCE_NOT_FOUND, resource, field, value);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String resource, String field, Object value)
    {
        super(buildMessage(resource, field, value));
        this.errorCode = Objects.requireNonNullElse(errorCode, ErrorCode.RESOURCE_NOT_FOUND);
        this.resource = resource;
        this.field = field;
        this.value = value;
    }

    private static String buildMessage(String resource, String field, Object value)
    {
        String r = (resource == null || resource.isBlank()) ? "Resource" : resource;
        String f = (field == null || field.isBlank()) ? "id" : field;

        return (value == null)
                ? (r + " not found")
                : (r + " not found with " + f + " = " + value);
    }
}