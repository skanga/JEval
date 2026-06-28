package dev.jeval.prompt;

public enum ToolMode {
    ALLOW_ADDITIONAL("ALLOW_ADDITIONAL"),
    NO_ADDITIONAL("NO_ADDITIONAL"),
    STRICT("STRICT");

    private final String value;

    ToolMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
