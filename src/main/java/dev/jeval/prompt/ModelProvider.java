package dev.jeval.prompt;

public enum ModelProvider {
    OPEN_AI("OPEN_AI"),
    ANTHROPIC("ANTHROPIC"),
    GEMINI("GEMINI"),
    X_AI("X_AI"),
    DEEPSEEK("DEEPSEEK"),
    BEDROCK("BEDROCK"),
    OPENROUTER("OPENROUTER");

    private final String value;

    ModelProvider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
