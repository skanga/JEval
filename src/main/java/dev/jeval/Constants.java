package dev.jeval;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class Constants {
    public static final String KEY_FILE = ".deepeval";
    public static final String HIDDEN_DIR = System.getenv().getOrDefault("DEEPEVAL_CACHE_FOLDER", ".deepeval");
    public static final String PYTEST_RUN_TEST_NAME = "CONFIDENT_AI_RUN_TEST_NAME";
    public static final String PYTEST_TRACE_TEST_WRAPPER_SPAN_NAME = "__deepeval_internal_pytest_test_wrapper__";
    public static final String LOGIN_PROMPT = "\nLooking for a place for your LLM test data to live? "
            + "Use Confident AI to get and share testing reports, experiment with models/prompts, "
            + "and catch regressions for your LLM system. Just run 'deepeval login' in the CLI.";
    public static final String CONFIDENT_TRACE_VERBOSE = "CONFIDENT_TRACE_VERBOSE";
    public static final String CONFIDENT_TRACE_FLUSH = "CONFIDENT_TRACE_FLUSH";
    public static final String CONFIDENT_TRACE_SAMPLE_RATE = "CONFIDENT_TRACE_SAMPLE_RATE";
    public static final String CONFIDENT_TRACE_ENVIRONMENT = "CONFIDENT_TRACE_ENVIRONMENT";
    public static final String CONFIDENT_TRACING_ENABLED = "CONFIDENT_TRACING_ENABLED";
    public static final String CONFIDENT_TRACE_INTERNAL = "CONFIDENT_TRACE_INTERNAL";
    public static final String CONFIDENT_OPEN_BROWSER = "CONFIDENT_OPEN_BROWSER";
    public static final String CONFIDENT_TEST_CASE_BATCH_SIZE = "CONFIDENT_TEST_CASE_BATCH_SIZE";
    public static final Set<String> SUPPORTED_PROVIDER_SLUGS = Arrays.stream(ProviderSlug.values())
            .map(ProviderSlug::value)
            .collect(Collectors.toUnmodifiableSet());

    private Constants() {
    }

    public static String slugify(ProviderSlug value) {
        return value.value();
    }

    public static String slugify(String value) {
        return String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    public enum ProviderSlug {
        OPENAI("openai"),
        AZURE("azure"),
        ANTHROPIC("anthropic"),
        BEDROCK("bedrock"),
        DEEPSEEK("deepseek"),
        GOOGLE("google"),
        GROK("grok"),
        KIMI("kimi"),
        LITELLM("litellm"),
        LOCAL("local"),
        OLLAMA("ollama"),
        OPENROUTER("openrouter"),
        PORTKEY("portkey");

        private final String value;

        ProviderSlug(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
