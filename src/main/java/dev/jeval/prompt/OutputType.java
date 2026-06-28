package dev.jeval.prompt;

public enum OutputType {
    TEXT("TEXT"),
    JSON("JSON"),
    SCHEMA("SCHEMA");

    private final String value;

    OutputType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
