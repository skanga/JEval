package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeyFileHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void writesFetchesAndRemovesNonSecretKeysLikeDeepEval() throws Exception {
        var handler = new KeyFileHandler(tempDir);

        handler.writeKey(KeyFileHandler.KeyValues.LAST_TEST_RUN_LINK, "https://app/report");

        assertEquals("https://app/report", handler.fetchData(KeyFileHandler.KeyValues.LAST_TEST_RUN_LINK));

        handler.removeKey(KeyFileHandler.KeyValues.LAST_TEST_RUN_LINK);

        assertNull(handler.fetchData(KeyFileHandler.KeyValues.LAST_TEST_RUN_LINK));
    }

    @Test
    void corruptedJsonFallsBackToEmptyDataLikeDeepEval() throws Exception {
        Files.writeString(tempDir.resolve(Constants.KEY_FILE), "{");

        var handler = new KeyFileHandler(tempDir);

        assertNull(handler.fetchData(KeyFileHandler.KeyValues.LAST_TEST_RUN_DATA));
    }

    @Test
    void refusesToPersistSecretKeysLikeDeepEval() throws Exception {
        var handler = new KeyFileHandler(tempDir);

        handler.writeKey(KeyFileHandler.ModelKeyValues.OPENAI_API_KEY, "sk-test");

        assertNull(handler.fetchData(KeyFileHandler.ModelKeyValues.OPENAI_API_KEY));
        assertNull(handler.path());
    }

    @Test
    void enumValuesMatchDeepEvalKeyHandler() {
        assertEquals("confident_api_key", KeyFileHandler.KeyValues.CONFIDENT_API_KEY.value());
        assertEquals("USE_OPENAI_MODEL", KeyFileHandler.ModelKeyValues.USE_OPENAI_MODEL.value());
        assertEquals("OPENROUTER_API_KEY", KeyFileHandler.ModelKeyValues.OPENROUTER_API_KEY.value());
        assertEquals("DEEPSEEK_BASE_URL", KeyFileHandler.ModelKeyValues.DEEPSEEK_BASE_URL.value());
        assertEquals("GEMINI_COST_PER_INPUT_TOKEN",
                KeyFileHandler.ModelKeyValues.GEMINI_COST_PER_INPUT_TOKEN.value());
        assertEquals("GEMINI_COST_PER_OUTPUT_TOKEN",
                KeyFileHandler.ModelKeyValues.GEMINI_COST_PER_OUTPUT_TOKEN.value());
        assertEquals("LOCAL_EMBEDDING_API_KEY", KeyFileHandler.EmbeddingKeyValues.LOCAL_EMBEDDING_API_KEY.value());
    }
}
