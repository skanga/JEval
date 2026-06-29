package dev.jeval.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CliSettings {
    private static final List<String> LLM_FLAGS = List.of(
            "USE_OPENAI_MODEL", "USE_AZURE_OPENAI", "USE_ANTHROPIC_MODEL", "USE_AWS_BEDROCK_MODEL",
            "USE_LOCAL_MODEL", "USE_GROK_MODEL", "USE_MOONSHOT_MODEL", "USE_DEEPSEEK_MODEL",
            "USE_GEMINI_MODEL", "USE_LITELLM", "USE_PORTKEY_MODEL", "USE_OPENROUTER_MODEL");
    private static final List<String> EMBED_FLAGS = List.of("USE_AZURE_OPENAI_EMBEDDING", "USE_LOCAL_EMBEDDINGS");
    private static final List<String> LLM_VALUES = List.of(
            "OPENAI_MODEL_NAME", "AZURE_MODEL_NAME", "AZURE_DEPLOYMENT_NAME", "AZURE_OPENAI_ENDPOINT",
            "OPENAI_API_VERSION", "ANTHROPIC_MODEL_NAME", "AWS_BEDROCK_MODEL_NAME", "AWS_BEDROCK_REGION",
            "OLLAMA_MODEL_NAME", "LOCAL_MODEL_NAME", "LOCAL_MODEL_BASE_URL", "LOCAL_MODEL_FORMAT",
            "GROK_MODEL_NAME", "MOONSHOT_MODEL_NAME", "DEEPSEEK_MODEL_NAME", "GEMINI_MODEL_NAME",
            "GOOGLE_CLOUD_PROJECT", "GOOGLE_CLOUD_LOCATION", "GOOGLE_GENAI_USE_VERTEXAI",
            "LITELLM_MODEL_NAME", "LITELLM_API_BASE",
            "LITELLM_PROXY_API_BASE", "PORTKEY_MODEL_NAME", "PORTKEY_BASE_URL", "PORTKEY_PROVIDER_NAME",
            "OPENROUTER_MODEL_NAME", "OPENROUTER_BASE_URL", "OPENROUTER_COST_PER_INPUT_TOKEN",
            "OPENROUTER_COST_PER_OUTPUT_TOKEN");
    private static final List<String> EMBED_VALUES = List.of(
            "AZURE_EMBEDDING_MODEL_NAME", "AZURE_EMBEDDING_DEPLOYMENT_NAME",
            "LOCAL_EMBEDDING_MODEL_NAME", "LOCAL_EMBEDDING_BASE_URL");

    private CliSettings() {
    }

    static int settings(String[] args, PrintStream out, PrintStream err) {
        try {
            var parsed = parse(args, 1);
            var dotenv = new DotenvFile(parsed.save());
            var updates = new LinkedHashMap<String, String>();
            var removals = new java.util.ArrayList<String>();
            for (var update : parsed.updates()) {
                var index = update.indexOf('=');
                if (index < 1) {
                    err.println("Expected key=value: " + update);
                    return 2;
                }
                updates.put(settingKey(update.substring(0, index)), settingValue(update.substring(0, index), update.substring(index + 1)));
            }
            for (var unset : parsed.unsets()) {
                removals.add(settingKey(unset));
            }
            if (!updates.isEmpty() || !removals.isEmpty()) {
                dotenv.update(updates, removals);
            }
            if (parsed.listFilter() != null) {
                list(dotenv, parsed.listFilter(), out);
            } else if (!parsed.quiet() && updates.isEmpty() && removals.isEmpty()) {
                out.println("No changes to save");
            }
            return 0;
        } catch (IOException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    static int setDebug(String[] args, PrintStream out, PrintStream err) {
        var save = savePath(args);
        var quiet = has(args, "--quiet");
        var level = option(args, "--log-level", "DEBUG");
        try {
            new DotenvFile(save).update(Map.of("LOG_LEVEL", logLevel(level)), List.of());
            if (!quiet) {
                out.println("LOG_LEVEL=" + logLevel(level));
            }
            return 0;
        } catch (IOException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    static int provider(String command, String[] args, PrintStream err) {
        var spec = ProviderSpec.forCommand(command);
        if (spec == null) {
            return -1;
        }
        try {
            var updates = new LinkedHashMap<String, String>();
            var removals = new java.util.ArrayList<String>();
            removals.addAll(spec.groupFlags());
            removals.addAll(spec.unsetKeys());
            if (command.startsWith("unset-")) {
                removals.add(spec.useKey());
                if (has(args, "--clear-secrets")) {
                    removals.addAll(spec.secretKeys());
                }
            } else {
                updates.put(spec.useKey(), "YES");
                for (var entry : spec.setKeys().entrySet()) {
                    var value = option(args, entry.getKey(), null);
                    if (value != null) {
                        updates.put(entry.getValue(), value);
                    }
                }
                updates.putAll(spec.derivedUpdates(args));
            }
            new DotenvFile(savePath(args)).update(updates, removals);
            return 0;
        } catch (IOException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static void list(DotenvFile dotenv, String filter, PrintStream out) throws IOException {
        var needle = filter.toUpperCase(Locale.ROOT).replace("-", "_");
        dotenv.read().forEach((key, value) -> {
            if (key.contains(needle)) {
                out.println(key + "=" + (secret(key) ? "********" : value));
            }
        });
    }

    private static boolean secret(String key) {
        return key.contains("KEY") || key.contains("TOKEN") || key.contains("SECRET");
    }

    private static Parsed parse(String[] args, int start) {
        var updates = new java.util.ArrayList<String>();
        var unsets = new java.util.ArrayList<String>();
        String list = null;
        var quiet = false;
        var save = Path.of(".env");
        for (var i = start; i < args.length; i++) {
            switch (args[i]) {
                case "-u", "--update" -> updates.add(args[++i]);
                case "-U", "--unset" -> unsets.add(args[++i]);
                case "-l", "--list" -> {
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        list = args[++i];
                    } else {
                        list = "";
                    }
                }
                case "--quiet" -> quiet = true;
                case "--save" -> save = savePath(args[++i]);
                default -> {
                }
            }
        }
        return new Parsed(updates, unsets, list, quiet, save);
    }

    private static String settingKey(String key) {
        return key.strip().toUpperCase(Locale.ROOT).replace("-", "_");
    }

    private static String settingValue(String key, String value) {
        return settingKey(key).equals("LOG_LEVEL") ? logLevel(value) : value;
    }

    private static String logLevel(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DEBUG" -> "10";
            case "INFO" -> "20";
            case "WARNING", "WARN" -> "30";
            case "ERROR" -> "40";
            default -> value;
        };
    }

    private static Path savePath(String[] args) {
        for (var i = 0; i < args.length - 1; i++) {
            if ("--save".equals(args[i])) {
                return savePath(args[i + 1]);
            }
        }
        return Path.of(".env");
    }

    private static Path savePath(String value) {
        return value.startsWith("dotenv:") ? Path.of(value.substring("dotenv:".length())) : Path.of(value);
    }

    private static String option(String[] args, String name, String fallback) {
        for (var i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    private static boolean has(String[] args, String name) {
        for (var arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private record Parsed(List<String> updates, List<String> unsets, String listFilter, boolean quiet, Path save) {
    }

    private record ProviderSpec(
            String useKey,
            Map<String, String> setKeys,
            List<String> groupFlags,
            List<String> unsetKeys,
            List<String> secretKeys) {
        static ProviderSpec forCommand(String command) {
            return switch (command) {
                case "set-openai", "unset-openai" -> openAi();
                case "set-anthropic", "unset-anthropic" -> llmWithSecrets(
                        "USE_ANTHROPIC_MODEL",
                        Map.of("--model", "ANTHROPIC_MODEL_NAME"),
                        "ANTHROPIC_API_KEY");
                case "set-ollama", "unset-ollama" -> llmWithSecrets(
                        "USE_LOCAL_MODEL",
                        Map.of("--model", "OLLAMA_MODEL_NAME", "--base-url", "LOCAL_MODEL_BASE_URL"),
                        "LOCAL_MODEL_API_KEY");
                case "set-local-model", "unset-local-model" -> llmWithSecrets(
                        "USE_LOCAL_MODEL",
                        Map.of("--model", "LOCAL_MODEL_NAME", "--base-url", "LOCAL_MODEL_BASE_URL",
                                "--format", "LOCAL_MODEL_FORMAT"),
                        "LOCAL_MODEL_API_KEY");
                case "set-azure-openai", "unset-azure-openai" -> llmWithSecrets(
                        "USE_AZURE_OPENAI",
                        Map.of("--model", "AZURE_MODEL_NAME", "--deployment-name", "AZURE_DEPLOYMENT_NAME",
                                "--base-url", "AZURE_OPENAI_ENDPOINT", "--api-version", "OPENAI_API_VERSION"),
                        "AZURE_OPENAI_API_KEY");
                case "set-bedrock", "unset-bedrock" -> llmWithSecrets(
                        "USE_AWS_BEDROCK_MODEL",
                        Map.of("--model", "AWS_BEDROCK_MODEL_NAME", "--region", "AWS_BEDROCK_REGION"),
                        "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY");
                case "set-grok", "unset-grok" -> llmWithSecrets(
                        "USE_GROK_MODEL",
                        Map.of("--model", "GROK_MODEL_NAME"),
                        "GROK_API_KEY");
                case "set-moonshot", "unset-moonshot" -> llmWithSecrets(
                        "USE_MOONSHOT_MODEL",
                        Map.of("--model", "MOONSHOT_MODEL_NAME"),
                        "MOONSHOT_API_KEY");
                case "set-deepseek", "unset-deepseek" -> llmWithSecrets(
                        "USE_DEEPSEEK_MODEL",
                        Map.of("--model", "DEEPSEEK_MODEL_NAME"),
                        "DEEPSEEK_API_KEY");
                case "set-gemini", "unset-gemini" -> gemini();
                case "set-litellm", "unset-litellm" -> llmWithSecrets(
                        "USE_LITELLM",
                        Map.of("--model", "LITELLM_MODEL_NAME", "--base-url", "LITELLM_API_BASE",
                                "--proxy-base-url", "LITELLM_PROXY_API_BASE"),
                        "LITELLM_API_KEY", "LITELLM_PROXY_API_KEY");
                case "set-portkey", "unset-portkey" -> llmWithSecrets(
                        "USE_PORTKEY_MODEL",
                        Map.of("--model", "PORTKEY_MODEL_NAME", "--base-url", "PORTKEY_BASE_URL",
                                "--provider", "PORTKEY_PROVIDER_NAME"),
                        "PORTKEY_API_KEY");
                case "set-openrouter", "unset-openrouter" -> openRouter();
                case "set-azure-openai-embedding", "unset-azure-openai-embedding" -> embed(
                        "USE_AZURE_OPENAI_EMBEDDING",
                        Map.of("--model", "AZURE_EMBEDDING_MODEL_NAME",
                                "--deployment-name", "AZURE_EMBEDDING_DEPLOYMENT_NAME"));
                case "set-local-embeddings", "unset-local-embeddings",
                        "set-ollama-embeddings", "unset-ollama-embeddings" -> embed(
                                "USE_LOCAL_EMBEDDINGS",
                                Map.of("--model", "LOCAL_EMBEDDING_MODEL_NAME",
                                        "--base-url", "LOCAL_EMBEDDING_BASE_URL"),
                                List.of("LOCAL_EMBEDDING_API_KEY"));
                default -> null;
            };
        }

        private static ProviderSpec openAi() {
            var setKeys = Map.of(
                    "--model", "OPENAI_MODEL_NAME",
                    "--temperature", "TEMPERATURE",
                    "--cost-per-input-token", "OPENAI_COST_PER_INPUT_TOKEN",
                    "--cost-per-output-token", "OPENAI_COST_PER_OUTPUT_TOKEN");
            return llm("USE_OPENAI_MODEL", setKeys, List.of(
                    "OPENAI_MODEL_NAME",
                    "OPENAI_COST_PER_INPUT_TOKEN",
                    "OPENAI_COST_PER_OUTPUT_TOKEN"), List.of("OPENAI_API_KEY"));
        }

        private static ProviderSpec openRouter() {
            var setKeys = Map.of(
                    "--model", "OPENROUTER_MODEL_NAME",
                    "--base-url", "OPENROUTER_BASE_URL",
                    "--temperature", "TEMPERATURE",
                    "--cost-per-input-token", "OPENROUTER_COST_PER_INPUT_TOKEN",
                    "--cost-per-output-token", "OPENROUTER_COST_PER_OUTPUT_TOKEN");
            return llm("USE_OPENROUTER_MODEL", setKeys, List.of(
                    "OPENROUTER_MODEL_NAME",
                    "OPENROUTER_BASE_URL",
                    "OPENROUTER_COST_PER_INPUT_TOKEN",
                    "OPENROUTER_COST_PER_OUTPUT_TOKEN"), List.of("OPENROUTER_API_KEY"));
        }

        private static ProviderSpec gemini() {
            var setKeys = Map.of(
                    "--model", "GEMINI_MODEL_NAME",
                    "--project", "GOOGLE_CLOUD_PROJECT",
                    "--location", "GOOGLE_CLOUD_LOCATION");
            return llm("USE_GEMINI_MODEL", setKeys, List.of(
                    "GEMINI_MODEL_NAME",
                    "GOOGLE_CLOUD_PROJECT",
                    "GOOGLE_CLOUD_LOCATION",
                    "GOOGLE_GENAI_USE_VERTEXAI"), List.of("GOOGLE_API_KEY", "GOOGLE_SERVICE_ACCOUNT_KEY"));
        }

        Map<String, String> derivedUpdates(String[] args) throws IOException {
            if (!"USE_GEMINI_MODEL".equals(useKey)) {
                return Map.of();
            }
            var serviceAccountFile = option(args, "--service-account-file", null);
            if (serviceAccountFile != null) {
                var updates = new LinkedHashMap<String, String>();
                updates.put("GOOGLE_SERVICE_ACCOUNT_KEY", Files.readString(Path.of(serviceAccountFile)));
                updates.put("GOOGLE_GENAI_USE_VERTEXAI", "true");
                return updates;
            }
            if (option(args, "--project", null) != null || option(args, "--location", null) != null) {
                return Map.of("GOOGLE_GENAI_USE_VERTEXAI", "true");
            }
            return Map.of();
        }

        private static ProviderSpec llm(String useKey, Map<String, String> keys) {
            return llm(useKey, keys, List.copyOf(keys.values()));
        }

        private static ProviderSpec llm(String useKey, Map<String, String> keys, List<String> unsetKeys) {
            return llm(useKey, keys, unsetKeys, List.of());
        }

        private static ProviderSpec llmWithSecrets(
                String useKey,
                Map<String, String> keys,
                String... secretKeys) {
            return llm(useKey, keys, List.copyOf(keys.values()), List.of(secretKeys));
        }

        private static ProviderSpec llm(
                String useKey,
                Map<String, String> keys,
                List<String> unsetKeys,
                List<String> secretKeys) {
            var removals = new java.util.ArrayList<String>();
            removals.addAll(LLM_FLAGS);
            removals.addAll(LLM_VALUES);
            return new ProviderSpec(useKey, keys, removals, unsetKeys, secretKeys);
        }

        private static ProviderSpec embed(String useKey, Map<String, String> keys) {
            return embed(useKey, keys, List.of());
        }

        private static ProviderSpec embed(String useKey, Map<String, String> keys, List<String> secretKeys) {
            var removals = new java.util.ArrayList<String>();
            removals.addAll(EMBED_FLAGS);
            removals.addAll(EMBED_VALUES);
            return new ProviderSpec(useKey, keys, removals, List.copyOf(keys.values()), secretKeys);
        }
    }
}
