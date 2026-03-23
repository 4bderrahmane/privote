package org.privote.backend.exception;

import org.privote.backend.utilities.ErrorCode;

public class OperationFailedException extends BaseException
{
    public OperationFailedException(String message)
    {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message);
    }

    public OperationFailedException(String message, Throwable cause)
    {
        super(ErrorCode.INTERNAL_SERVER_ERROR, message, cause);
    }
}
