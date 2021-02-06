package com.revents;

public class NotFollowedReventsConventionException extends ReventsException {

    private static final long serialVersionUID = -743502169237344272L;

    public NotFollowedReventsConventionException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotFollowedReventsConventionException(String message) {
        super(message);
    }
}
