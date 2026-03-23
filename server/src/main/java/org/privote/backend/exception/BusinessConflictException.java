package org.privote.backend.exception;

import org.privote.backend.utilities.ErrorCode;

public class BusinessConflictException extends BaseException
{
    public BusinessConflictException(String message)
    {
        super(ErrorCode.DATA_CONFLICT, message);
    }

    public BusinessConflictException(String message, Throwable cause)
    {
        super(ErrorCode.DATA_CONFLICT, message, cause);
    }
}
