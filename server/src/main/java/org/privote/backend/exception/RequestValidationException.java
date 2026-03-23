package org.privote.backend.exception;

import org.privote.backend.utilities.ErrorCode;

public class RequestValidationException extends BaseException
{
    public RequestValidationException(String message)
    {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public RequestValidationException(String message, Throwable cause)
    {
        super(ErrorCode.VALIDATION_ERROR, message, cause);
    }
}
