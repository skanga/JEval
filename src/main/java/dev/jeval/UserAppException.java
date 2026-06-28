package dev.jeval;

public final class UserAppException extends RuntimeException {
    public UserAppException(String message) {
        super(message);
    }

    public UserAppException(String message, Throwable cause) {
        super(message, cause);
    }
}
