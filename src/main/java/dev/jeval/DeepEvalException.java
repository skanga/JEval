package dev.jeval;

public class DeepEvalException extends RuntimeException {
    public DeepEvalException(String message) {
        super(message);
    }

    public DeepEvalException(String message, Throwable cause) {
        super(message, cause);
    }
}
