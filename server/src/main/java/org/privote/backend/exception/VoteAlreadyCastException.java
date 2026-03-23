package org.privote.backend.exception;

public class VoteAlreadyCastException extends RuntimeException
{
    public VoteAlreadyCastException(String message)
    {
        super(message);
    }
}
