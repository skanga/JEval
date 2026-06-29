package dev.jeval.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.langchain4j.LangChain4jEvaluationModel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LangChain4jProviderModelsTest {
    @TempDir
    Path tempDir;

    @Test
    void createsOpenAiModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_OPENAI_MODEL=YES
                OPENAI_MODEL_NAME=gpt-4o-mini
                OPENAI_API_KEY=sk-test
                TEMPERATURE=0.2
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsOllamaModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LOCAL_MODEL=YES
                OLLAMA_MODEL_NAME=llama3
                LOCAL_MODEL_BASE_URL=http://localhost:11434
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsOpenRouterModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_OPENROUTER_MODEL=YES
                OPENROUTER_MODEL_NAME=openai/gpt-4.1
                OPENROUTER_API_KEY=sk-or-test
                OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
                TEMPERATURE=0.2
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void rejectsMissingProviderConfig() throws Exception {
        var error = assertThrows(IllegalArgumentException.class,
                () -> LangChain4jProviderModels.from(new DotenvFile(tempDir.resolve(".env"))));

        assertEquals("No supported provider is configured; run set-openai, set-ollama, or set-openrouter, or pass --responses-file.",
                error.getMessage());
    }
}
