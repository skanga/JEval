package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PromptApiEnumTest {

    @Test
    void reasoningEffortValuesMatchDeepEval() {
        assertEquals("MINIMAL", ReasoningEffort.MINIMAL.value());
        assertEquals("LOW", ReasoningEffort.LOW.value());
        assertEquals("MEDIUM", ReasoningEffort.MEDIUM.value());
        assertEquals("HIGH", ReasoningEffort.HIGH.value());
    }

    @Test
    void verbosityValuesMatchDeepEval() {
        assertEquals("LOW", Verbosity.LOW.value());
        assertEquals("MEDIUM", Verbosity.MEDIUM.value());
        assertEquals("HIGH", Verbosity.HIGH.value());
    }

    @Test
    void modelProviderValuesMatchDeepEval() {
        assertEquals("OPEN_AI", ModelProvider.OPEN_AI.value());
        assertEquals("ANTHROPIC", ModelProvider.ANTHROPIC.value());
        assertEquals("GEMINI", ModelProvider.GEMINI.value());
        assertEquals("X_AI", ModelProvider.X_AI.value());
        assertEquals("DEEPSEEK", ModelProvider.DEEPSEEK.value());
        assertEquals("BEDROCK", ModelProvider.BEDROCK.value());
        assertEquals("OPENROUTER", ModelProvider.OPENROUTER.value());
    }

    @Test
    void toolModeValuesMatchDeepEval() {
        assertEquals("ALLOW_ADDITIONAL", ToolMode.ALLOW_ADDITIONAL.value());
        assertEquals("NO_ADDITIONAL", ToolMode.NO_ADDITIONAL.value());
        assertEquals("STRICT", ToolMode.STRICT.value());
    }

    @Test
    void outputTypeValuesMatchDeepEval() {
        assertEquals("TEXT", OutputType.TEXT.value());
        assertEquals("JSON", OutputType.JSON.value());
        assertEquals("SCHEMA", OutputType.SCHEMA.value());
    }

    @Test
    void schemaDataTypeValuesMatchDeepEval() {
        assertEquals("OBJECT", SchemaDataType.OBJECT.value());
        assertEquals("ARRAY", SchemaDataType.ARRAY.value());
        assertEquals("STRING", SchemaDataType.STRING.value());
        assertEquals("FLOAT", SchemaDataType.FLOAT.value());
        assertEquals("INTEGER", SchemaDataType.INTEGER.value());
        assertEquals("BOOLEAN", SchemaDataType.BOOLEAN.value());
        assertEquals("NULL", SchemaDataType.NULL.value());
    }
}
