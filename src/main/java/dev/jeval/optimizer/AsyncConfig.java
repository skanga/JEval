package dev.jeval.optimizer;

public record AsyncConfig(boolean runAsync, double throttleValue, int maxConcurrent) {

    public AsyncConfig() {
        this(true, 0.0, 20);
    }

    public AsyncConfig {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("'max_concurrent' must be at least 1");
        }
        if (throttleValue < 0.0) {
            throw new IllegalArgumentException("'throttle_value' must be at least 0");
        }
    }
}
