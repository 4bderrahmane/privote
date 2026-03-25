package org.privote.backend.exception;

import lombok.Getter;
import org.privote.backend.utilities.ErrorCode;

@Getter
public abstract class BaseException extends RuntimeException
{
    private final ErrorCode errorCode;

    protected BaseException(ErrorCode errorCode, String message)
    {
        super(message);
        this.errorCode = errorCode;
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause)
    {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
