package dev.jeval.prompt;

public enum Verbosity {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String value;

    Verbosity(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
