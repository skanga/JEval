package dev.jeval.tracing;

public enum Provider {
    OPEN_AI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Gemini"),
    X_AI("XAI"),
    DEEP_SEEK("DeepSeek"),
    MISTRAL("Mistral"),
    PERPLEXITY("Perplexity"),
    BEDROCK("Bedrock"),
    VERTEX_AI("VertexAI"),
    AZURE("Azure"),
    OPEN_ROUTER("OpenRouter"),
    PORTKEY("Portkey"),
    TRUE_FOUNDRY("TrueFoundry"),
    MOONSHOT("Moonshot");

    private final String value;

    Provider(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
