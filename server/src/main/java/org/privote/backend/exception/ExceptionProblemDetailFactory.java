package org.privote.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.privote.backend.utilities.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Component
final class ExceptionProblemDetailFactory
{
    private static final String ERRORS = "errors";
    private static final String TIMESTAMP = "timestamp";
    private static final String ERROR_CODE = "errorCode";
    private static final String REQUEST_ID = "requestId";

    private final ExceptionContextResolver contextResolver;

    ExceptionProblemDetailFactory(ExceptionContextResolver contextResolver)
    {
        this.contextResolver = contextResolver;
    }

    static String reasonPhrase(HttpStatusCode status)
    {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved != null)
        {
            return resolved.getReasonPhrase();
        }

        return "HTTP " + status.value();
    }

    private static void setPropertyIfAbsent(ProblemDetail problemDetail, String key, @Nullable Object value)
    {
        if (value == null) return;

        Map<String, Object> properties = problemDetail.getProperties();
        if (properties != null && properties.containsKey(key)) return;

        problemDetail.setProperty(key, value);
    }

    private static HttpStatus resolveHttpStatus(ErrorCode errorCode)
    {
        return HttpStatus.valueOf(errorCode.httpStatus());
    }

    ProblemDetail buildProblemDetail(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable HttpStatusCode responseStatus
    )
    {
        HttpStatusCode status = responseStatus != null ? responseStatus : resolveHttpStatus(errorCode);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);

        problemDetail.setTitle(reasonPhrase(status));
        problemDetail.setType(URI.create("urn:problem-type:" + errorCode.name().toLowerCase().replace("_", "-")));

        if (request != null)
            problemDetail.setInstance(URI.create(request.getRequestURI()));

        problemDetail.setProperty(TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        problemDetail.setProperty(ERROR_CODE, errorCode.name());
        setCorrelationProperties(problemDetail, request);

        if (errors != null)
            problemDetail.setProperty(ERRORS, errors);

        return problemDetail;
    }

    void enrichProblemDetail(ProblemDetail problemDetail, ErrorCode errorCode, @Nullable HttpServletRequest request)
    {
        String title = problemDetail.getTitle();
        if (title == null || title.isBlank())
        {
            problemDetail.setTitle(reasonPhrase(HttpStatusCode.valueOf(problemDetail.getStatus())));
        }

        if (problemDetail.getType() == null)
        {
            problemDetail.setType(URI.create("urn:problem-type:" + errorCode.name().toLowerCase().replace("_", "-")));
        }

        if (request != null)
        {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
        }

        setPropertyIfAbsent(problemDetail, TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        setPropertyIfAbsent(problemDetail, ERROR_CODE, errorCode.name());
        setCorrelationProperties(problemDetail, request);
    }

    private void setCorrelationProperties(ProblemDetail problemDetail, @Nullable HttpServletRequest request)
    {
        setPropertyIfAbsent(problemDetail, REQUEST_ID, contextResolver.resolveRequestId(request));
    }
}
