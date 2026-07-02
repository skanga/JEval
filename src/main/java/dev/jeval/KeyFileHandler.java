package dev.jeval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class KeyFileHandler {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    public static final KeyFileHandler KEY_FILE_HANDLER = new KeyFileHandler();

    private final Path directory;
    private final Path file;

    public KeyFileHandler() {
        this(Path.of(Constants.HIDDEN_DIR));
    }

    public KeyFileHandler(Path directory) {
        this.directory = directory;
        this.file = directory.resolve(Constants.KEY_FILE);
    }

    public void writeKey(Key key, Object value) throws IOException {
        if (isSecretKey(key)) {
            return;
        }
        var data = read();
        data.put(key.value(), value);
        Files.createDirectories(directory);
        JSON.writeValue(file.toFile(), data);
    }

    public Object fetchData(Key key) throws IOException {
        return read().get(key.value());
    }

    public void removeKey(Key key) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        var data = read();
        data.remove(key.value());
        Files.createDirectories(directory);
        JSON.writeValue(file.toFile(), data);
    }

    public Path path() {
        return Files.exists(file) ? file : null;
    }

    private LinkedHashMap<String, Object> read() throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(file.toFile(), MAP_TYPE);
        } catch (IOException ignored) {
            return new LinkedHashMap<>();
        }
    }

    private static boolean isSecretKey(Key key) {
        var name = key.name();
        return name.endsWith("API_KEY")
                || name.endsWith("SECRET_ACCESS_KEY")
                || name.endsWith("SERVICE_ACCOUNT_KEY");
    }

    public interface Key {
        String name();

        String value();
    }

    public enum KeyValues implements Key {
        CONFIDENT_API_KEY("confident_api_key"),
        CONFIDENT_BASE_URL("confident_base_url"),
        CONFIDENT_REGION("confident_region"),
        LAST_TEST_RUN_LINK("last_test_run_link"),
        LAST_TEST_RUN_DATA("last_test_run_data");

        private final String value;

        KeyValues(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum ModelKeyValues implements Key {
        TEMPERATURE("TEMPERATURE"),
        USE_ANTHROPIC_MODEL("USE_ANTHROPIC_MODEL"),
        ANTHROPIC_API_KEY("ANTHROPIC_API_KEY"),
        ANTHROPIC_MODEL_NAME("ANTHROPIC_MODEL_NAME"),
        ANTHROPIC_COST_PER_INPUT_TOKEN("ANTHROPIC_COST_PER_INPUT_TOKEN"),
        ANTHROPIC_COST_PER_OUTPUT_TOKEN("ANTHROPIC_COST_PER_OUTPUT_TOKEN"),
        AWS_ACCESS_KEY_ID("AWS_ACCESS_KEY_ID"),
        AWS_SECRET_ACCESS_KEY("AWS_SECRET_ACCESS_KEY"),
        USE_AWS_BEDROCK_MODEL("USE_AWS_BEDROCK_MODEL"),
        AWS_BEDROCK_MODEL_NAME("AWS_BEDROCK_MODEL_NAME"),
        AWS_BEDROCK_REGION("AWS_BEDROCK_REGION"),
        AWS_BEDROCK_COST_PER_INPUT_TOKEN("AWS_BEDROCK_COST_PER_INPUT_TOKEN"),
        AWS_BEDROCK_COST_PER_OUTPUT_TOKEN("AWS_BEDROCK_COST_PER_OUTPUT_TOKEN"),
        AZURE_OPENAI_API_KEY("AZURE_OPENAI_API_KEY"),
        AZURE_OPENAI_ENDPOINT("AZURE_OPENAI_ENDPOINT"),
        OPENAI_API_VERSION("OPENAI_API_VERSION"),
        AZURE_DEPLOYMENT_NAME("AZURE_DEPLOYMENT_NAME"),
        AZURE_MODEL_NAME("AZURE_MODEL_NAME"),
        AZURE_MODEL_VERSION("AZURE_MODEL_VERSION"),
        USE_AZURE_OPENAI("USE_AZURE_OPENAI"),
        USE_DEEPSEEK_MODEL("USE_DEEPSEEK_MODEL"),
        DEEPSEEK_API_KEY("DEEPSEEK_API_KEY"),
        DEEPSEEK_MODEL_NAME("DEEPSEEK_MODEL_NAME"),
        DEEPSEEK_BASE_URL("DEEPSEEK_BASE_URL"),
        DEEPSEEK_COST_PER_INPUT_TOKEN("DEEPSEEK_COST_PER_INPUT_TOKEN"),
        DEEPSEEK_COST_PER_OUTPUT_TOKEN("DEEPSEEK_COST_PER_OUTPUT_TOKEN"),
        USE_GEMINI_MODEL("USE_GEMINI_MODEL"),
        GOOGLE_API_KEY("GOOGLE_API_KEY"),
        GEMINI_MODEL_NAME("GEMINI_MODEL_NAME"),
        GEMINI_COST_PER_INPUT_TOKEN("GEMINI_COST_PER_INPUT_TOKEN"),
        GEMINI_COST_PER_OUTPUT_TOKEN("GEMINI_COST_PER_OUTPUT_TOKEN"),
        GOOGLE_GENAI_USE_VERTEXAI("GOOGLE_GENAI_USE_VERTEXAI"),
        GOOGLE_CLOUD_PROJECT("GOOGLE_CLOUD_PROJECT"),
        GOOGLE_CLOUD_LOCATION("GOOGLE_CLOUD_LOCATION"),
        GOOGLE_SERVICE_ACCOUNT_KEY("GOOGLE_SERVICE_ACCOUNT_KEY"),
        USE_GROK_MODEL("USE_GROK_MODEL"),
        GROK_API_KEY("GROK_API_KEY"),
        GROK_MODEL_NAME("GROK_MODEL_NAME"),
        GROK_COST_PER_INPUT_TOKEN("GROK_COST_PER_INPUT_TOKEN"),
        GROK_COST_PER_OUTPUT_TOKEN("GROK_COST_PER_OUTPUT_TOKEN"),
        USE_LITELLM("USE_LITELLM"),
        LITELLM_API_KEY("LITELLM_API_KEY"),
        LITELLM_MODEL_NAME("LITELLM_MODEL_NAME"),
        LITELLM_API_BASE("LITELLM_API_BASE"),
        LITELLM_PROXY_API_BASE("LITELLM_PROXY_API_BASE"),
        LITELLM_PROXY_API_KEY("LITELLM_PROXY_API_KEY"),
        LM_STUDIO_API_KEY("LM_STUDIO_API_KEY"),
        LM_STUDIO_MODEL_NAME("LM_STUDIO_MODEL_NAME"),
        USE_LOCAL_MODEL("USE_LOCAL_MODEL"),
        LOCAL_MODEL_API_KEY("LOCAL_MODEL_API_KEY"),
        LOCAL_MODEL_NAME("LOCAL_MODEL_NAME"),
        LOCAL_MODEL_BASE_URL("LOCAL_MODEL_BASE_URL"),
        LOCAL_MODEL_FORMAT("LOCAL_MODEL_FORMAT"),
        USE_MOONSHOT_MODEL("USE_MOONSHOT_MODEL"),
        MOONSHOT_API_KEY("MOONSHOT_API_KEY"),
        MOONSHOT_MODEL_NAME("MOONSHOT_MODEL_NAME"),
        MOONSHOT_COST_PER_INPUT_TOKEN("MOONSHOT_COST_PER_INPUT_TOKEN"),
        MOONSHOT_COST_PER_OUTPUT_TOKEN("MOONSHOT_COST_PER_OUTPUT_TOKEN"),
        OLLAMA_MODEL_NAME("OLLAMA_MODEL_NAME"),
        USE_OPENAI_MODEL("USE_OPENAI_MODEL"),
        OPENAI_API_KEY("OPENAI_API_KEY"),
        OPENAI_MODEL_NAME("OPENAI_MODEL_NAME"),
        OPENAI_COST_PER_INPUT_TOKEN("OPENAI_COST_PER_INPUT_TOKEN"),
        OPENAI_COST_PER_OUTPUT_TOKEN("OPENAI_COST_PER_OUTPUT_TOKEN"),
        USE_PORTKEY_MODEL("USE_PORTKEY_MODEL"),
        PORTKEY_API_KEY("PORTKEY_API_KEY"),
        PORTKEY_MODEL_NAME("PORTKEY_MODEL_NAME"),
        PORTKEY_BASE_URL("PORTKEY_BASE_URL"),
        PORTKEY_PROVIDER_NAME("PORTKEY_PROVIDER_NAME"),
        VERTEX_AI_MODEL_NAME("VERTEX_AI_MODEL_NAME"),
        VLLM_API_KEY("VLLM_API_KEY"),
        VLLM_MODEL_NAME("VLLM_MODEL_NAME"),
        USE_OPENROUTER_MODEL("USE_OPENROUTER_MODEL"),
        OPENROUTER_MODEL_NAME("OPENROUTER_MODEL_NAME"),
        OPENROUTER_COST_PER_INPUT_TOKEN("OPENROUTER_COST_PER_INPUT_TOKEN"),
        OPENROUTER_COST_PER_OUTPUT_TOKEN("OPENROUTER_COST_PER_OUTPUT_TOKEN"),
        OPENROUTER_API_KEY("OPENROUTER_API_KEY");

        private final String value;

        ModelKeyValues(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public enum EmbeddingKeyValues implements Key {
        USE_AZURE_OPENAI_EMBEDDING("USE_AZURE_OPENAI_EMBEDDING"),
        AZURE_EMBEDDING_MODEL_NAME("AZURE_EMBEDDING_MODEL_NAME"),
        AZURE_EMBEDDING_DEPLOYMENT_NAME("AZURE_EMBEDDING_DEPLOYMENT_NAME"),
        USE_LOCAL_EMBEDDINGS("USE_LOCAL_EMBEDDINGS"),
        LOCAL_EMBEDDING_MODEL_NAME("LOCAL_EMBEDDING_MODEL_NAME"),
        LOCAL_EMBEDDING_BASE_URL("LOCAL_EMBEDDING_BASE_URL"),
        LOCAL_EMBEDDING_API_KEY("LOCAL_EMBEDDING_API_KEY");

        private final String value;

        EmbeddingKeyValues(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
