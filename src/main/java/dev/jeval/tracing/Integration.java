package dev.jeval.tracing;

public enum Integration {
    LANGCHAIN("LangChain"),
    CREW_AI("CrewAI"),
    LLAMA_INDEX("LlamaIndex"),
    OPENAI_AGENTS("OpenAI Agents"),
    OPEN_AI("OpenAI"),
    ANTHROPIC("Anthropic"),
    PYDANTIC_AI("PydanticAI"),
    GOOGLE_ADK("Google ADK"),
    STRANDS("Strands"),
    OTEL("OpenTelemetry"),
    OPEN_INFERENCE("OpenInference"),
    AGENTCORE("AgentCore");

    private final String value;

    Integration(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
