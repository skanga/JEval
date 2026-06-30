package dev.jeval.cli;

import com.azure.core.http.jdk.httpclient.JdkHttpClientProvider;
import dev.jeval.EvaluationModel;
import dev.jeval.langchain4j.LangChain4jEvaluationModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.util.Map;

final class LangChain4jProviderModels {
    private LangChain4jProviderModels() {
    }

    static EvaluationModel from(DotenvFile dotenv) throws IOException {
        return from(dotenv, null);
    }

    static EvaluationModel from(DotenvFile dotenv, String modelOverride) throws IOException {
        var config = dotenv.read();
        if ("YES".equals(config.get("USE_OPENAI_MODEL"))) {
            return new LangChain4jEvaluationModel(openAi(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_AZURE_OPENAI"))) {
            return new LangChain4jEvaluationModel(azureOpenAi(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_OPENROUTER_MODEL"))) {
            return new LangChain4jEvaluationModel(openRouter(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_ANTHROPIC_MODEL"))) {
            return new LangChain4jEvaluationModel(anthropic(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_GEMINI_MODEL"))
                && !"true".equalsIgnoreCase(value(config, "GOOGLE_GENAI_USE_VERTEXAI", "false"))) {
            return new LangChain4jEvaluationModel(gemini(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_DEEPSEEK_MODEL"))) {
            return new LangChain4jEvaluationModel(deepSeek(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_GROK_MODEL"))) {
            return new LangChain4jEvaluationModel(grok(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_MOONSHOT_MODEL"))) {
            return new LangChain4jEvaluationModel(moonshot(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_LITELLM"))) {
            return new LangChain4jEvaluationModel(liteLlm(config, modelOverride));
        }
        if ("YES".equals(config.get("USE_LOCAL_MODEL"))) {
            if (present(config, "OLLAMA_MODEL_NAME") || (present(modelOverride) && !localModelConfigured(config))) {
                return new LangChain4jEvaluationModel(ollama(config, modelOverride));
            }
            if (localModelConfigured(config) || present(modelOverride)) {
                return new LangChain4jEvaluationModel(localModel(config, modelOverride));
            }
        }
        throw new IllegalArgumentException(
                "No supported provider is configured; run set-openai, set-azure-openai, set-anthropic, set-gemini, set-grok, set-moonshot, set-deepseek, set-litellm, set-ollama, set-local-model, or set-openrouter, or pass --responses-file.");
    }

    private static OpenAiChatModel openAi(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "OPENAI_API_KEY"))
                .modelName(modelName(config, "OPENAI_MODEL_NAME", modelOverride, "gpt-4o-mini"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static AzureOpenAiChatModel azureOpenAi(Map<String, String> config, String modelOverride) {
        var builder = AzureOpenAiChatModel.builder()
                .endpoint(required(config, "AZURE_OPENAI_ENDPOINT"))
                .apiKey(required(config, "AZURE_OPENAI_API_KEY"))
                .deploymentName(deploymentName(config, modelOverride))
                .serviceVersion(value(config, "OPENAI_API_VERSION", "2024-06-01"))
                .httpClientProvider(new JdkHttpClientProvider());
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OpenAiChatModel openRouter(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "OPENROUTER_API_KEY"))
                .modelName(modelName(config, "OPENROUTER_MODEL_NAME", modelOverride, null))
                .baseUrl(value(config, "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static AnthropicChatModel anthropic(Map<String, String> config, String modelOverride) {
        var builder = AnthropicChatModel.builder()
                .apiKey(required(config, "ANTHROPIC_API_KEY"))
                .modelName(modelName(config, "ANTHROPIC_MODEL_NAME", modelOverride, "claude-3-5-haiku-latest"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static GoogleAiGeminiChatModel gemini(Map<String, String> config, String modelOverride) {
        var builder = GoogleAiGeminiChatModel.builder()
                .apiKey(required(config, "GOOGLE_API_KEY"))
                .modelName(modelName(config, "GEMINI_MODEL_NAME", modelOverride, "gemini-2.5-flash"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxOutputTokens);
        return builder.build();
    }

    private static OpenAiChatModel deepSeek(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "DEEPSEEK_API_KEY"))
                .modelName(modelName(config, "DEEPSEEK_MODEL_NAME", modelOverride, "deepseek-v4-flash"))
                .baseUrl(value(config, "DEEPSEEK_BASE_URL", "https://api.deepseek.com"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OpenAiChatModel grok(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "GROK_API_KEY"))
                .modelName(modelName(config, "GROK_MODEL_NAME", modelOverride, "grok-4.3"))
                .baseUrl(value(config, "GROK_BASE_URL", "https://api.x.ai/v1"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OpenAiChatModel moonshot(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "MOONSHOT_API_KEY"))
                .modelName(modelName(config, "MOONSHOT_MODEL_NAME", modelOverride, "kimi-k2.6"))
                .baseUrl(value(config, "MOONSHOT_BASE_URL", "https://api.moonshot.ai/v1"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OpenAiChatModel liteLlm(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(value(config, "LITELLM_API_KEY", value(config, "LITELLM_PROXY_API_KEY", "litellm")))
                .modelName(modelName(config, "LITELLM_MODEL_NAME", modelOverride, null))
                .baseUrl(value(config, "LITELLM_API_BASE", value(config, "LITELLM_PROXY_API_BASE", "http://localhost:4000")));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OllamaChatModel ollama(Map<String, String> config, String modelOverride) {
        var builder = OllamaChatModel.builder()
                .baseUrl(value(config, "LOCAL_MODEL_BASE_URL", "http://localhost:11434"))
                .modelName(modelName(config, "OLLAMA_MODEL_NAME", modelOverride, null));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::numPredict);
        return builder.build();
    }

    private static OpenAiChatModel localModel(Map<String, String> config, String modelOverride) {
        var builder = OpenAiChatModel.builder()
                .apiKey(value(config, "LOCAL_MODEL_API_KEY", "local"))
                .modelName(modelName(config, "LOCAL_MODEL_NAME", modelOverride, null))
                .baseUrl(value(config, "LOCAL_MODEL_BASE_URL", "http://localhost:8000/v1"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static boolean present(Map<String, String> config, String key) {
        return value(config, key, null) != null;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean localModelConfigured(Map<String, String> config) {
        return present(config, "LOCAL_MODEL_NAME")
                || present(config, "LOCAL_MODEL_API_KEY")
                || present(config, "LOCAL_MODEL_FORMAT");
    }

    private static String required(Map<String, String> config, String key) {
        var value = value(config, key, null);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required for provider-backed generation.");
        }
        return value;
    }

    private static String modelName(Map<String, String> config, String key, String modelOverride, String fallback) {
        if (modelOverride != null && !modelOverride.isBlank()) {
            return modelOverride;
        }
        var value = value(config, key, fallback);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required for provider-backed generation.");
        }
        return value;
    }

    private static String deploymentName(Map<String, String> config, String modelOverride) {
        var value = value(config, "AZURE_DEPLOYMENT_NAME", null);
        if (value != null) {
            return value;
        }
        return modelName(config, "AZURE_MODEL_NAME", modelOverride, null);
    }

    private static String value(Map<String, String> config, String key, String fallback) {
        var value = config.get(key);
        if (value == null || value.isBlank()) {
            value = System.getenv(key);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void optionalDouble(Map<String, String> config, String key, java.util.function.Consumer<Double> set) {
        var value = value(config, key, null);
        if (value != null) {
            set.accept(Double.parseDouble(value));
        }
    }

    private static void optionalInteger(Map<String, String> config, String key, java.util.function.Consumer<Integer> set) {
        var value = value(config, key, null);
        if (value != null) {
            set.accept(Integer.parseInt(value));
        }
    }
}
