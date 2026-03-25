package org.privote.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.privote.backend.utilities.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;

final class ExceptionLogService
{
    private static final Logger LOG = LoggerFactory.getLogger(ExceptionLogService.class);
    private static final int MAX_MESSAGE_LENGTH = 512;

    private final ExceptionContextResolver contextResolver;

    ExceptionLogService(ExceptionContextResolver contextResolver)
    {
        this.contextResolver = contextResolver;
    }

    void logKeycloakFailure(KeycloakAdminException ex, @Nullable HttpServletRequest request, ErrorCode errorCode)
    {
        HttpStatusCode status = HttpStatusCode.valueOf(ex.status());
        ExceptionContextResolver.ExceptionContext context = contextResolver.resolveContext(request);
        String message = contextResolver.sanitize(ex.getMessage(), MAX_MESSAGE_LENGTH);

        if (status.is5xxServerError() || errorCode == ErrorCode.EXTERNAL_SERVICE_FAILURE || errorCode == ErrorCode.TIMEOUT_OCCURRED)
        {
            LOG.error(
                    "Keycloak admin operation failed method={} path={} status={} errorCode={} requestId={} userId={} message={}",
                    context.method(),
                    context.path(),
                    status.value(),
                    errorCode.name(),
                    context.requestId(),
                    ex.getUserId(),
                    message,
                    ex
            );
            return;
        }

        LOG.warn(
                "Keycloak admin operation failed method={} path={} status={} errorCode={} requestId={} userId={} message={}",
                context.method(),
                context.path(),
                status.value(),
                errorCode.name(),
                context.requestId(),
                ex.getUserId(),
                message
        );
    }

    void logForStatus(
            HttpStatusCode statusCode,
            Exception ex,
            @Nullable HttpServletRequest request,
            @Nullable ErrorCode errorCode
    )
    {
        ExceptionContextResolver.ExceptionContext context = contextResolver.resolveContext(request);
        String resolvedErrorCode = errorCode != null ? errorCode.name() : "unknown";
        String message = contextResolver.sanitize(ex.getMessage(), MAX_MESSAGE_LENGTH);
        Throwable cause = ex.getCause();
        String causeMessage = cause == null ? null : contextResolver.sanitize(cause.getMessage(), MAX_MESSAGE_LENGTH);

        if (statusCode.is5xxServerError())
        {
            LOG.error(
                    "Request failed method={} path={} status={} errorCode={} requestId={} message={}",
                    context.method(),
                    context.path(),
                    statusCode.value(),
                    resolvedErrorCode,
                    context.requestId(),
                    message,
                    ex
            );
            return;
        }

        if (isRoutineValidation(statusCode, errorCode))
        {
            LOG.debug(
                    "Request rejected method={} path={} status={} errorCode={} requestId={} message={}",
                    context.method(),
                    context.path(),
                    statusCode.value(),
                    resolvedErrorCode,
                    context.requestId(),
                    message
            );
            return;
        }

        if (causeMessage != null)
        {
            LOG.warn(
                    "Request failed method={} path={} status={} errorCode={} requestId={} message={} cause={}",
                    context.method(),
                    context.path(),
                    statusCode.value(),
                    resolvedErrorCode,
                    context.requestId(),
                    message,
                    causeMessage
            );
            return;
        }

        LOG.warn(
                "Request failed method={} path={} status={} errorCode={} requestId={} message={}",
                context.method(),
                context.path(),
                statusCode.value(),
                resolvedErrorCode,
                context.requestId(),
                message
        );
    }

    private boolean isRoutineValidation(HttpStatusCode statusCode, @Nullable ErrorCode errorCode)
    {
        return statusCode.is4xxClientError() && errorCode == ErrorCode.VALIDATION_ERROR;
    }
}
