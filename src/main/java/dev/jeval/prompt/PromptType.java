package dev.jeval.prompt;

public enum PromptType {
    TEXT("TEXT"),
    LIST("LIST");

    private final String value;

    PromptType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
