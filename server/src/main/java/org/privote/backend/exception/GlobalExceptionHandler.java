package org.privote.backend.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.privote.backend.utilities.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler
{
    private final ExceptionProblemDetailFactory problemDetailFactory;
    private final ExceptionLogService exceptionLogService;

    private static void addValidationError(Map<String, List<String>> errors, String key, @Nullable String message)
    {
        String normalizedKey = (key == null || key.isBlank()) ? "request" : key;
        String normalizedMessage = (message == null || message.isBlank()) ? "Validation failed" : message;
        errors.computeIfAbsent(normalizedKey, ignored -> new ArrayList<>()).add(normalizedMessage);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ProblemDetail> handleBaseException(BaseException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(resolveHttpStatus(ex.getErrorCode()), ex, request, ex.getErrorCode());
        return buildResponse(ex.getErrorCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFoundException(ResourceNotFoundException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(resolveHttpStatus(ex.getErrorCode()), ex, request, ex.getErrorCode());
        return buildResponse(ex.getErrorCode(), notFoundDetail(ex.getResource()), request, null);
    }

    @ExceptionHandler(VoteAlreadyCastException.class)
    public ResponseEntity<ProblemDetail> handleVoteAlreadyCastException(VoteAlreadyCastException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.CONFLICT, ex, request, ErrorCode.DATA_CONFLICT);
        return buildResponse(ErrorCode.DATA_CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(KeycloakAdminException.class)
    public ResponseEntity<ProblemDetail> handleKeycloakAdminException(KeycloakAdminException ex, HttpServletRequest request)
    {
        ErrorCode errorCode = mapKeycloakStatus(ex.status());
        exceptionLogService.logKeycloakFailure(ex, request, errorCode);
        return buildResponse(errorCode, keycloakDetail(errorCode), request, null);
    }

    @ExceptionHandler(HandleAccessDenied.class)
    public ResponseEntity<ProblemDetail> handleCustomAccessDenied(HandleAccessDenied ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.FORBIDDEN, ex, request, ErrorCode.ACCESS_DENIED);
        return buildResponse(ErrorCode.ACCESS_DENIED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.FORBIDDEN, ex, request, ErrorCode.ACCESS_DENIED);
        return buildResponse(ErrorCode.ACCESS_DENIED, "Access denied", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.UNAUTHORIZED, ex, request, ErrorCode.UNAUTHORIZED);
        return buildResponse(ErrorCode.UNAUTHORIZED, "Authentication required", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request)
    {
        String detail = "Invalid value for parameter '" + ex.getName() + "'";
        exceptionLogService.logForStatus(HttpStatus.BAD_REQUEST, ex, request, ErrorCode.VALIDATION_ERROR);
        return buildResponse(ErrorCode.VALIDATION_ERROR, detail, request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request)
    {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                addValidationError(errors, violation.getPropertyPath().toString(), violation.getMessage())
        );

        exceptionLogService.logForStatus(HttpStatus.BAD_REQUEST, ex, request, ErrorCode.VALIDATION_ERROR);
        return buildResponse(ErrorCode.VALIDATION_ERROR, "Validation failed for one or more parameters.", request, errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.CONFLICT, ex, request, ErrorCode.DATA_CONFLICT);
        return buildResponse(ErrorCode.DATA_CONFLICT, "Data integrity constraint violated.", request, null);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailureException(OptimisticLockingFailureException ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.CONFLICT, ex, request, ErrorCode.DATA_CONFLICT);
        return buildResponse(ErrorCode.DATA_CONFLICT, "Concurrent modification detected. Retry the request.", request, null);
    }

    @ExceptionHandler({TransactionSystemException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ProblemDetail> handleTransactionFailureException(Exception ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, ErrorCode.INTERNAL_SERVER_ERROR);
        return buildResponse(ErrorCode.INTERNAL_SERVER_ERROR, "Database transaction failed.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknownException(Exception ex, HttpServletRequest request)
    {
        exceptionLogService.logForStatus(HttpStatus.INTERNAL_SERVER_ERROR, ex, request, ErrorCode.INTERNAL_SERVER_ERROR);
        return buildResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please contact support.",
                request,
                null
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
        HttpServletRequest servletRequest = extractRequest(request);
        return buildBindingValidationResponse(ex, ex.getBindingResult(), servletRequest, status);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Object> handleBindException(BindException ex, HttpServletRequest request)
    {
        return buildBindingValidationResponse(ex, ex.getBindingResult(), request, HttpStatus.BAD_REQUEST);
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleMissingServletRequestParameter(
            @NonNull MissingServletRequestParameterException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Missing required request parameter '" + ex.getParameterName() + "'.",
                servletRequest,
                null,
                status
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
        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Malformed request body.",
                servletRequest,
                null,
                status
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleHttpMediaTypeNotSupported(
            @NonNull HttpMediaTypeNotSupportedException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Unsupported media type.",
                servletRequest,
                null,
                status
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleHttpMediaTypeNotAcceptable(
            @NonNull HttpMediaTypeNotAcceptableException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Requested representation is not acceptable.",
                servletRequest,
                null,
                status
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleServletRequestBindingException(
            @NonNull ServletRequestBindingException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        String detail = ex.getMessage();
        if (detail == null || detail.isBlank())
        {
            detail = "Missing or invalid request binding value.";
        }

        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                detail,
                servletRequest,
                null,
                status
        );
    }

    @Override
    protected @NonNull ResponseEntity<@NonNull Object> handleMaxUploadSizeExceededException(
            @NonNull MaxUploadSizeExceededException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request
    )
    {
        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Uploaded payload exceeds the maximum allowed size.",
                servletRequest,
                null,
                status
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

        HttpServletRequest servletRequest = extractRequest(request);
        exceptionLogService.logForStatus(status, ex, servletRequest, errorCode);
        return buildObjectResponse(errorCode, detail, servletRequest, null, status);
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
            problemDetailFactory.enrichProblemDetail(problemDetail, errorCode, servletRequest);
            exceptionLogService.logForStatus(statusCode, ex, servletRequest, errorCode);
            return new ResponseEntity<>(problemDetail, headers, statusCode);
        }

        String detail = ex.getMessage();
        if (detail == null || detail.isBlank())
        {
            detail = ExceptionProblemDetailFactory.reasonPhrase(statusCode);
        }

        exceptionLogService.logForStatus(statusCode, ex, servletRequest, errorCode);
        return buildObjectResponse(errorCode, detail, servletRequest, null, statusCode);
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors
    )
    {
        return buildResponse(errorCode, detail, request, errors, null);
    }

    private ResponseEntity<ProblemDetail> buildResponse(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable HttpStatusCode responseStatus
    )
    {
        ProblemDetail problemDetail = problemDetailFactory.buildProblemDetail(errorCode, detail, request, errors, responseStatus);
        HttpStatusCode status = responseStatus != null ? responseStatus : resolveHttpStatus(errorCode);
        return new ResponseEntity<>(problemDetail, status);
    }

    private ResponseEntity<Object> buildObjectResponse(
            ErrorCode errorCode,
            String detail,
            @Nullable HttpServletRequest request,
            @Nullable Object errors,
            @Nullable HttpStatusCode responseStatus
    )
    {
        ProblemDetail problemDetail = problemDetailFactory.buildProblemDetail(errorCode, detail, request, errors, responseStatus);
        HttpStatusCode status = responseStatus != null ? responseStatus : resolveHttpStatus(errorCode);
        return ResponseEntity.status(status).body(problemDetail);
    }

    private ResponseEntity<Object> buildBindingValidationResponse(
            Exception ex,
            BindingResult bindingResult,
            @Nullable HttpServletRequest request,
            HttpStatusCode status
    )
    {
        Map<String, List<String>> errors = extractBindingErrors(bindingResult);
        exceptionLogService.logForStatus(status, ex, request, ErrorCode.VALIDATION_ERROR);
        return buildObjectResponse(
                ErrorCode.VALIDATION_ERROR,
                "Validation failed for one or more fields.",
                request,
                errors,
                status
        );
    }

    private Map<String, List<String>> extractBindingErrors(BindingResult bindingResult)
    {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error ->
                addValidationError(errors, error.getField(), error.getDefaultMessage())
        );
        bindingResult.getGlobalErrors().forEach(error ->
                addValidationError(errors, error.getObjectName(), error.getDefaultMessage())
        );
        return errors;
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

    private String notFoundDetail(@Nullable String resource)
    {
        if (resource == null || resource.isBlank())
        {
            return "Resource not found";
        }
        return resource + " not found";
    }

    private String keycloakDetail(ErrorCode errorCode)
    {
        return switch (errorCode)
        {
            case UNAUTHORIZED -> "Authentication required";
            case ACCESS_DENIED -> "Access denied";
            case USER_NOT_FOUND -> "User not found";
            case TIMEOUT_OCCURRED -> "Identity service request timed out";
            default -> "Identity service request failed";
        };
    }

    private ErrorCode mapStatusToErrorCode(HttpStatusCode status)
    {
        return switch (status.value())
        {
            case 400, 406, 413, 415, 422 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> ErrorCode.DATA_CONFLICT;
            case 429 -> ErrorCode.OPERATION_NOT_ALLOWED;
            case 408, 504 -> ErrorCode.TIMEOUT_OCCURRED;
            case 502, 503 -> ErrorCode.EXTERNAL_SERVICE_FAILURE;
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
