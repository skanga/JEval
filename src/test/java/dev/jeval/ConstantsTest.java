package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstantsTest {

    @Test
    void providerSlugValuesMatchDeepEvalConstants() {
        assertEquals("openai", Constants.ProviderSlug.OPENAI.value());
        assertEquals("azure", Constants.ProviderSlug.AZURE.value());
        assertEquals("anthropic", Constants.ProviderSlug.ANTHROPIC.value());
        assertEquals("bedrock", Constants.ProviderSlug.BEDROCK.value());
        assertEquals("deepseek", Constants.ProviderSlug.DEEPSEEK.value());
        assertEquals("google", Constants.ProviderSlug.GOOGLE.value());
        assertEquals("grok", Constants.ProviderSlug.GROK.value());
        assertEquals("kimi", Constants.ProviderSlug.KIMI.value());
        assertEquals("litellm", Constants.ProviderSlug.LITELLM.value());
        assertEquals("local", Constants.ProviderSlug.LOCAL.value());
        assertEquals("ollama", Constants.ProviderSlug.OLLAMA.value());
        assertEquals("openrouter", Constants.ProviderSlug.OPENROUTER.value());
        assertEquals("portkey", Constants.ProviderSlug.PORTKEY.value());
    }

    @Test
    void slugifyMatchesDeepEvalConstants() {
        assertEquals("openai", Constants.slugify(Constants.ProviderSlug.OPENAI));
        assertEquals("anthropic", Constants.slugify("  Anthropic  "));
    }

    @Test
    void supportedProviderSlugsMatchDeepEvalConstants() {
        assertEquals(Set.of(
                "openai", "azure", "anthropic", "bedrock", "deepseek", "google", "grok",
                "kimi", "litellm", "local", "ollama", "openrouter", "portkey"),
                Constants.SUPPORTED_PROVIDER_SLUGS);
    }

    @Test
    void constantNamesMatchDeepEvalConstants() {
        assertEquals(".deepeval", Constants.KEY_FILE);
        assertEquals("CONFIDENT_AI_RUN_TEST_NAME", Constants.PYTEST_RUN_TEST_NAME);
        assertEquals("__deepeval_internal_pytest_test_wrapper__", Constants.PYTEST_TRACE_TEST_WRAPPER_SPAN_NAME);
        assertTrue(Constants.LOGIN_PROMPT.contains("deepeval login"));
    }
}
