package org.privote.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.privote.backend.infrastructure.logging.RequestCorrelationFilter;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
final class ExceptionContextResolver
{
    private static final String UNKNOWN_CONTEXT_VALUE = "unknown";
    private static final String MDC_REQUEST_ID_KEY = "requestId";
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_METHOD_LENGTH = 16;
    private static final int MAX_PATH_LENGTH = 512;

    ExceptionContext resolveContext(@Nullable HttpServletRequest request)
    {
        String method = request != null ? sanitize(request.getMethod(), MAX_METHOD_LENGTH) : null;
        String path = request != null ? sanitize(request.getRequestURI(), MAX_PATH_LENGTH) : null;
        String requestId = resolveRequestId(request);

        return new ExceptionContext(
                defaultValue(method),
                defaultValue(path),
                defaultValue(requestId)
        );
    }

    @Nullable String resolveRequestId(@Nullable HttpServletRequest request)
    {
        String fromMdc = sanitize(MDC.get(MDC_REQUEST_ID_KEY), MAX_IDENTIFIER_LENGTH);
        if (fromMdc != null)
        {
            return fromMdc;
        }

        if (request == null)
        {
            return null;
        }

        return sanitize(request.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER), MAX_IDENTIFIER_LENGTH);
    }

    @Nullable String sanitize(@Nullable String value, int maxLength)
    {
        if (value == null)
            return null;

        String normalized = value.trim().replace('\n', ' ').replace('\r', ' ');

        if (normalized.isEmpty())
            return null;

        if (normalized.length() > maxLength)
            return normalized.substring(0, maxLength);

        return normalized;
    }

    private String defaultValue(@Nullable String value)
    {
        return value == null ? UNKNOWN_CONTEXT_VALUE : value;
    }

    record ExceptionContext(String method, String path, String requestId)
    {
    }
}
