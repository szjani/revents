package com.revents;

public class ReventsException extends RuntimeException {

    private static final long serialVersionUID = 7735167569095926869L;

    public ReventsException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReventsException(String message) {
        super(message);
    }
}
