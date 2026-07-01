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
    void rejectsInvalidProviderNumericSettingWithSettingName() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_OPENAI_MODEL=YES
                OPENAI_MODEL_NAME=gpt-4o-mini
                OPENAI_API_KEY=sk-test
                TEMPERATURE=hot
                """);

        var error = assertThrows(IllegalArgumentException.class,
                () -> LangChain4jProviderModels.from(new DotenvFile(env)));

        assertEquals("Invalid value for TEMPERATURE: hot", error.getMessage());
    }

    @Test
    void createsAzureOpenAiModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_AZURE_OPENAI=YES
                AZURE_OPENAI_ENDPOINT=https://jeval.openai.azure.com/
                AZURE_OPENAI_API_KEY=azure-test
                AZURE_DEPLOYMENT_NAME=prod-deployment
                OPENAI_API_VERSION=2024-06-01
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsBedrockModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_AWS_BEDROCK_MODEL=YES
                AWS_BEDROCK_MODEL_NAME=anthropic.claude-3-5-haiku-20241022-v1:0
                AWS_BEDROCK_REGION=us-east-1
                AWS_ACCESS_KEY_ID=access-test
                AWS_SECRET_ACCESS_KEY=secret-test
                TEMPERATURE=0.2
                MAX_TOKENS=128
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
    void createsOpenAiCompatibleLocalModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LOCAL_MODEL=YES
                LOCAL_MODEL_NAME=local-eval
                LOCAL_MODEL_BASE_URL=http://localhost:8000/v1
                LOCAL_MODEL_API_KEY=local-key
                TEMPERATURE=0.2
                MAX_TOKENS=128
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
    void createsAnthropicModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_ANTHROPIC_MODEL=YES
                ANTHROPIC_MODEL_NAME=claude-3-5-haiku-latest
                ANTHROPIC_API_KEY=sk-ant-test
                TEMPERATURE=0.2
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsGeminiModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_GEMINI_MODEL=YES
                GEMINI_MODEL_NAME=gemini-2.5-flash
                GOOGLE_API_KEY=google-test
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsDeepSeekModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_DEEPSEEK_MODEL=YES
                DEEPSEEK_MODEL_NAME=deepseek-v4-flash
                DEEPSEEK_API_KEY=sk-ds-test
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsGrokModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_GROK_MODEL=YES
                GROK_MODEL_NAME=grok-4.3
                GROK_API_KEY=xai-test
                GROK_BASE_URL=https://api.x.ai/v1
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsMoonshotModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_MOONSHOT_MODEL=YES
                MOONSHOT_MODEL_NAME=kimi-k2.6
                MOONSHOT_API_KEY=moonshot-test
                MOONSHOT_BASE_URL=https://api.moonshot.ai/v1
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsLiteLlmModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LITELLM=YES
                LITELLM_MODEL_NAME=gpt-4o-mini
                LITELLM_API_BASE=http://localhost:4000
                LITELLM_API_KEY=litellm-test
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsPortkeyModelFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_PORTKEY_MODEL=YES
                PORTKEY_MODEL_NAME=@openai-prod/gpt-4o-mini
                PORTKEY_BASE_URL=https://api.portkey.ai/v1
                PORTKEY_API_KEY=portkey-test
                TEMPERATURE=0.2
                MAX_TOKENS=128
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForOpenRouterGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_OPENROUTER_MODEL=YES
                OPENROUTER_API_KEY=sk-or-test
                OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "openai/gpt-4.1");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForAzureOpenAiDeploymentLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_AZURE_OPENAI=YES
                AZURE_OPENAI_ENDPOINT=https://jeval.openai.azure.com/
                AZURE_OPENAI_API_KEY=azure-test
                OPENAI_API_VERSION=2024-06-01
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "prod-deployment");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesAzureModelNameWhenDeploymentNameIsMissing() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_AZURE_OPENAI=YES
                AZURE_OPENAI_ENDPOINT=https://jeval.openai.azure.com/
                AZURE_OPENAI_API_KEY=azure-test
                AZURE_MODEL_NAME=gpt-4.1
                OPENAI_API_VERSION=2024-06-01
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForBedrockGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_AWS_BEDROCK_MODEL=YES
                AWS_BEDROCK_REGION=us-west-2
                AWS_ACCESS_KEY_ID=access-test
                AWS_SECRET_ACCESS_KEY=secret-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "amazon.nova-lite-v1:0");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForAnthropicGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_ANTHROPIC_MODEL=YES
                ANTHROPIC_API_KEY=sk-ant-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "claude-3-5-haiku-latest");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForGeminiGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_GEMINI_MODEL=YES
                GOOGLE_API_KEY=google-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "gemini-2.5-flash");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForDeepSeekGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_DEEPSEEK_MODEL=YES
                DEEPSEEK_API_KEY=sk-ds-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "deepseek-v4-pro");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForGrokGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_GROK_MODEL=YES
                GROK_API_KEY=xai-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "grok-4.3");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForMoonshotGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_MOONSHOT_MODEL=YES
                MOONSHOT_API_KEY=moonshot-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "moonshot-v1-8k");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForLiteLlmGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LITELLM=YES
                LITELLM_API_BASE=http://localhost:4000
                LITELLM_API_KEY=litellm-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "openai/gpt-4.1");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void createsLiteLlmModelFromProxyBaseSetting() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LITELLM=YES
                LITELLM_MODEL_NAME=anthropic/claude-3-5-haiku
                LITELLM_PROXY_API_BASE=http://localhost:4000
                LITELLM_PROXY_API_KEY=proxy-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env));

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForPortkeyGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_PORTKEY_MODEL=YES
                PORTKEY_PROVIDER_NAME=openai-prod
                PORTKEY_API_KEY=portkey-test
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "gpt-4.1");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForOllamaGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LOCAL_MODEL=YES
                LOCAL_MODEL_BASE_URL=http://localhost:11434
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "llama3");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void usesModelOverrideForOpenAiCompatibleLocalGenerationLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_LOCAL_MODEL=YES
                LOCAL_MODEL_BASE_URL=http://localhost:8000/v1
                LOCAL_MODEL_API_KEY=local-key
                """);

        var model = LangChain4jProviderModels.from(new DotenvFile(env), "local-eval");

        assertNotNull(model);
        assertInstanceOf(LangChain4jEvaluationModel.class, model);
    }

    @Test
    void rejectsMissingProviderConfig() throws Exception {
        var error = assertThrows(IllegalArgumentException.class,
                () -> LangChain4jProviderModels.from(new DotenvFile(tempDir.resolve(".env"))));

        assertEquals("No supported provider is configured; run set-openai, set-azure-openai, set-bedrock, set-anthropic, set-gemini, set-grok, set-moonshot, set-deepseek, set-litellm, set-portkey, set-ollama, set-local-model, or set-openrouter, or pass --responses-file.",
                error.getMessage());
    }
}
