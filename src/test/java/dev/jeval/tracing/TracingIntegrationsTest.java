package dev.jeval.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TracingIntegrationsTest {

    @Test
    void integrationValuesMatchDeepEvalTracingPayloadStrings() {
        assertEquals("LangChain", Integration.LANGCHAIN.value());
        assertEquals("CrewAI", Integration.CREW_AI.value());
        assertEquals("LlamaIndex", Integration.LLAMA_INDEX.value());
        assertEquals("OpenAI Agents", Integration.OPENAI_AGENTS.value());
        assertEquals("OpenAI", Integration.OPEN_AI.value());
        assertEquals("Anthropic", Integration.ANTHROPIC.value());
        assertEquals("PydanticAI", Integration.PYDANTIC_AI.value());
        assertEquals("Google ADK", Integration.GOOGLE_ADK.value());
        assertEquals("Strands", Integration.STRANDS.value());
        assertEquals("OpenTelemetry", Integration.OTEL.value());
        assertEquals("OpenInference", Integration.OPEN_INFERENCE.value());
        assertEquals("AgentCore", Integration.AGENTCORE.value());
    }

    @Test
    void providerValuesMatchDeepEvalTracingPayloadStrings() {
        assertEquals("OpenAI", Provider.OPEN_AI.value());
        assertEquals("Anthropic", Provider.ANTHROPIC.value());
        assertEquals("Gemini", Provider.GEMINI.value());
        assertEquals("XAI", Provider.X_AI.value());
        assertEquals("DeepSeek", Provider.DEEP_SEEK.value());
        assertEquals("Mistral", Provider.MISTRAL.value());
        assertEquals("Perplexity", Provider.PERPLEXITY.value());
        assertEquals("Bedrock", Provider.BEDROCK.value());
        assertEquals("VertexAI", Provider.VERTEX_AI.value());
        assertEquals("Azure", Provider.AZURE.value());
        assertEquals("OpenRouter", Provider.OPEN_ROUTER.value());
        assertEquals("Portkey", Provider.PORTKEY.value());
        assertEquals("TrueFoundry", Provider.TRUE_FOUNDRY.value());
        assertEquals("Moonshot", Provider.MOONSHOT.value());
    }
}
