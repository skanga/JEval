package dev.jeval.cli;

import dev.jeval.EvaluationModel;
import dev.jeval.langchain4j.LangChain4jEvaluationModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.IOException;
import java.util.Map;

final class LangChain4jProviderModels {
    private LangChain4jProviderModels() {
    }

    static EvaluationModel from(DotenvFile dotenv) throws IOException {
        var config = dotenv.read();
        if ("YES".equals(config.get("USE_OPENAI_MODEL"))) {
            return new LangChain4jEvaluationModel(openAi(config));
        }
        if ("YES".equals(config.get("USE_OPENROUTER_MODEL"))) {
            return new LangChain4jEvaluationModel(openRouter(config));
        }
        if ("YES".equals(config.get("USE_LOCAL_MODEL")) && present(config, "OLLAMA_MODEL_NAME")) {
            return new LangChain4jEvaluationModel(ollama(config));
        }
        throw new IllegalArgumentException(
                "No supported provider is configured; run set-openai, set-ollama, or set-openrouter, or pass --responses-file.");
    }

    private static OpenAiChatModel openAi(Map<String, String> config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "OPENAI_API_KEY"))
                .modelName(value(config, "OPENAI_MODEL_NAME", "gpt-4o-mini"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OpenAiChatModel openRouter(Map<String, String> config) {
        var builder = OpenAiChatModel.builder()
                .apiKey(required(config, "OPENROUTER_API_KEY"))
                .modelName(required(config, "OPENROUTER_MODEL_NAME"))
                .baseUrl(value(config, "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::maxTokens);
        return builder.build();
    }

    private static OllamaChatModel ollama(Map<String, String> config) {
        var builder = OllamaChatModel.builder()
                .baseUrl(value(config, "LOCAL_MODEL_BASE_URL", "http://localhost:11434"))
                .modelName(required(config, "OLLAMA_MODEL_NAME"));
        optionalDouble(config, "TEMPERATURE", builder::temperature);
        optionalInteger(config, "MAX_TOKENS", builder::numPredict);
        return builder.build();
    }

    private static boolean present(Map<String, String> config, String key) {
        return value(config, key, null) != null;
    }

    private static String required(Map<String, String> config, String key) {
        var value = value(config, key, null);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required for provider-backed generation.");
        }
        return value;
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
