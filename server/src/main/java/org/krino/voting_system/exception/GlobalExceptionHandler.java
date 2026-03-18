package org.krino.voting_system.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.krino.voting_system.utilities.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PATH = "path";
    private static final String METHOD = "method";
    private static final String ERRORS = "errors";
    private static final String TIMESTAMP = "timestamp";
    private static final String ERROR_CODE = "errorCode";
    private static final String EXCEPTION = "exception";

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ProblemDetail> handleBaseException(BaseException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null, null, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.VALIDATION_ERROR, ex.getMessage(), request, null, null, ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleIllegalStateException(IllegalStateException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.DATA_CONFLICT, ex.getMessage(), request, null, null, ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request)
    {
        logClientException(ex);

        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("resource", ex.getResource());
        extras.put("field", ex.getField());
        extras.put("value", ex.getValue());

        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null, extras, ex);
    }

    @ExceptionHandler(VoteAlreadyCastException.class)
    public ResponseEntity<ProblemDetail> handleVoteAlreadyCastException(VoteAlreadyCastException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.DATA_CONFLICT, ex.getMessage(), request, null, null, ex);
    }

    @ExceptionHandler(KeycloakAdminException.class)
    public ResponseEntity<ProblemDetail> handleKeycloakAdminException(KeycloakAdminException ex, HttpServletRequest request)
    {
        ErrorCode errorCode = mapKeycloakStatus(ex.status());
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("upstreamStatus", ex.status());
        extras.put("userId", ex.getUserId());

        if (errorCode == ErrorCode.EXTERNAL_SERVICE_FAILURE || errorCode == ErrorCode.TIMEOUT_OCCURRED)
        {
            LOG.error("Keycloak admin operation failed: status={}, userId={}", ex.status(), ex.getUserId(), ex);
        } else
        {
            logClientException(ex);
        }

        return buildResponse(errorCode, ex.getMessage(), request, null, extras, ex);
    }

    @ExceptionHandler(HandleAccessDenied.class)
    public ResponseEntity<ProblemDetail> handleCustomAccessDenied(HandleAccessDenied ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.ACCESS_DENIED, ex.getMessage(), request, null, null, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.ACCESS_DENIED, "Access denied", request, null, null, ex);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request)
    {
        logClientException(ex);
        return buildResponse(ErrorCode.UNAUTHORIZED, "Authentication required", request, null, null, ex);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request)
    {
        String detail = "Invalid value for parameter '" + ex.getName() + "'";
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("parameter", ex.getName());
        extras.put("value", ex.getValue());

        logClientException(ex);
        return buildResponse(ErrorCode.VALIDATION_ERROR, detail, request, null, extras, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request)
    {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation -> errors.put(
                violation.getPropertyPath().toString(),
                violation.getMessage()
        ));

        logClientException(ex);
        return buildResponse(ErrorCode.VALIDATION_ERROR, "Validation failed for one or more parameters.", request, errors, null, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknownException(Exception ex, HttpServletRequest request)
    {
        LOG.error("Unhandled exception occurred", ex);
        return buildResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.",
                request,
                null,
                null,
                ex
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        logClientException(ex);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed for one or more fields.",
                extractRequest(request),
                errors,
                null,
                ex
        );
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex, HttpServletRequest request)
    {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        logClientException(ex);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed for one or more fields.",
                request,
                errors,
                null,
                ex
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        Map<String, Object> extras = Map.of(
                "parameter", ex.getParameterName(),
                "expectedType", ex.getParameterType()
        );

        logClientException(ex);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Missing required request parameter '" + ex.getParameterName() + "'.",
                extractRequest(request),
                null,
                extras,
                ex
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleHttpMessageNotReadable(
            @NonNull HttpMessageNotReadableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        logClientException(ex);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Malformed request body.",
                extractRequest(request),
                null,
                null,
                ex
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleErrorResponseException(
            @NonNull ErrorResponseException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        ErrorCode errorCode = mapStatusToErrorCode(status);
        String detail = ex.getBody().getDetail();
        if (detail == null || detail.isBlank())
        {
            detail = status.toString();
        }

        logForStatus(status, ex);
        return buildObjectResponse(errorCode, detail, extractRequest(request), null, null, ex);
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleExceptionInternal(
            @NonNull Exception ex,
            @Nullable Object body,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode statusCode,
            @NonNull WebRequest request
    )
    {
        HttpServletRequest servletRequest = extractRequest(request);
        ErrorCode errorCode = mapStatusToErrorCode(statusCode);

        if (body instanceof ProblemDetail problemDetail)
        {
            enrichProblemDetail(problemDetail, errorCode, servletRequest, ex);
            logForStatus(statusCode, ex);
            return new ResponseEntity<>(problemDetail, headers, statusCode);
        }

        String detail = ex.getMessage();
        if (detail == null || detail.isBlank())
        {
            detail = HttpStatus.valueOf(statusCode.value()).getReasonPhrase();
        }

        logForStatus(statusCode, ex);
        return buildObjectResponse(errorCode, detail, servletRequest, null, null, ex);
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable Map<String, ?> extras,
            @Nullable Throwable ex
    )
    {
        ProblemDetail problemDetail = buildProblemDetail(errorCode, detail, request, errors, extras, ex);
        return new ResponseEntity<>(problemDetail, resolveHttpStatus(errorCode));
    }

    private ResponseEntity<Object> buildObjectResponse(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable Map<String, ?> extras,
            @Nullable Throwable ex
    )
    {
        ProblemDetail problemDetail = buildProblemDetail(errorCode, detail, request, errors, extras, ex);
        return ResponseEntity.status(resolveHttpStatus(errorCode)).body(problemDetail);
    }

    private ProblemDetail buildProblemDetail(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable Map<String, ?> extras,
            @Nullable Throwable ex
    )
    {
        HttpStatus status = HttpStatus.valueOf(errorCode.httpStatus());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);

        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setType(URI.create("urn:problem-type:" + errorCode.name().toLowerCase().replace("_", "-")));

        if (request != null)
        {
            String instance = request.getRequestURI();
            if (request.getQueryString() != null)
            {
                instance += "?" + request.getQueryString();
            }
            problemDetail.setInstance(URI.create(instance));
            problemDetail.setProperty(PATH, request.getRequestURI());
            problemDetail.setProperty(METHOD, request.getMethod());
        }

        problemDetail.setProperty(TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        problemDetail.setProperty(ERROR_CODE, errorCode.name());

        if (errors != null)
        {
            problemDetail.setProperty(ERRORS, errors);
        }

        if (extras != null && !extras.isEmpty())
        {
            extras.forEach((key, value) -> setPropertyIfAbsent(problemDetail, key, value));
        }

        if (ex != null)
        {
            setPropertyIfAbsent(problemDetail, EXCEPTION, ex.getClass().getSimpleName());
        }

        return problemDetail;
    }

    private void enrichProblemDetail(
            ProblemDetail problemDetail,
            ErrorCode errorCode,
            @Nullable HttpServletRequest request,
            @Nullable Throwable ex
    )
    {
        String title = problemDetail.getTitle();
        if (title == null || title.isBlank())
        {
            HttpStatus status = HttpStatus.valueOf(problemDetail.getStatus());
            problemDetail.setTitle(status.getReasonPhrase());
        }

        if (problemDetail.getType() == null)
        {
            problemDetail.setType(URI.create("urn:problem-type:" + errorCode.name().toLowerCase().replace("_", "-")));
        }

        if (request != null)
        {
            if (problemDetail.getInstance() == null)
            {
                String instance = request.getRequestURI();
                if (request.getQueryString() != null)
                {
                    instance += "?" + request.getQueryString();
                }
                problemDetail.setInstance(URI.create(instance));
            }
            setPropertyIfAbsent(problemDetail, PATH, request.getRequestURI());
            setPropertyIfAbsent(problemDetail, METHOD, request.getMethod());
        }

        setPropertyIfAbsent(problemDetail, TIMESTAMP, OffsetDateTime.now(ZoneOffset.UTC));
        setPropertyIfAbsent(problemDetail, ERROR_CODE, errorCode.name());
        if (ex != null)
        {
            setPropertyIfAbsent(problemDetail, EXCEPTION, ex.getClass().getSimpleName());
        }
    }

    private static void setPropertyIfAbsent(ProblemDetail problemDetail, String key, @Nullable Object value)
    {
        if (value == null)
        {
            return;
        }

        Map<String, Object> properties = problemDetail.getProperties();
        if (properties != null && properties.containsKey(key))
        {
            return;
        }

        problemDetail.setProperty(key, value);
    }

    private @Nullable HttpServletRequest extractRequest(WebRequest request)
    {
        if (request instanceof ServletWebRequest servletWebRequest)
        {
            return servletWebRequest.getRequest();
        }
        return null;
    }

    private HttpStatus resolveHttpStatus(ErrorCode errorCode)
    {
        return HttpStatus.valueOf(errorCode.httpStatus());
    }

    private void logClientException(Exception ex)
    {
        LOG.warn("Request failed: {}", ex.getMessage());
    }

    private void logForStatus(HttpStatusCode statusCode, Exception ex)
    {
        if (statusCode.is5xxServerError())
        {
            LOG.error("Request failed with server error", ex);
            return;
        }
        logClientException(ex);
    }

    private ErrorCode mapStatusToErrorCode(HttpStatusCode status)
    {
        return switch (status.value())
        {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.DATA_CONFLICT;
            case 502, 503 -> ErrorCode.EXTERNAL_SERVICE_FAILURE;
            case 504 -> ErrorCode.TIMEOUT_OCCURRED;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    private ErrorCode mapKeycloakStatus(int status)
    {
        return switch (status)
        {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.USER_NOT_FOUND;
            case 408, 504 -> ErrorCode.TIMEOUT_OCCURRED;
            default -> ErrorCode.EXTERNAL_SERVICE_FAILURE;
        };
    }
}
