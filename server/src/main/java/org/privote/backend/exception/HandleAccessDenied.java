package org.privote.backend.exception;

public class HandleAccessDenied extends RuntimeException
{
    public HandleAccessDenied(String message)
    {
        super(message);
    }
}
