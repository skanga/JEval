package dev.jeval.benchmarks;

public enum ARCMode {
    EASY("ARC-Easy"),
    CHALLENGE("ARC-Challenge");

    private final String value;

    ARCMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
