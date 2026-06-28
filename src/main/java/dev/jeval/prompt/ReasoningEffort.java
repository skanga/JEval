package dev.jeval.prompt;

public enum ReasoningEffort {
    MINIMAL("MINIMAL"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    private final String value;

    ReasoningEffort(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
