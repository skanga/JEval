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
    private static final Map<String, String> DEPRECATED_COMPUTED_SETTING_KEYS = Map.of(
            "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS", "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE",
            "DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS", "DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE",
            "DEEPEVAL_TASK_GATHER_BUFFER_SECONDS", "DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE");
    private static final List<String> DEBUG_KEYS = List.of(
            "LOG_LEVEL",
            "DEEPEVAL_VERBOSE_MODE",
            "DEEPEVAL_DEBUG_ASYNC",
            "DEEPEVAL_LOG_STACK_TRACES",
            "DEEPEVAL_RETRY_BEFORE_LOG_LEVEL",
            "DEEPEVAL_RETRY_AFTER_LOG_LEVEL",
            "DEEPEVAL_GRPC_LOGGING",
            "GRPC_VERBOSITY",
            "GRPC_TRACE",
            "CONFIDENT_TRACE_VERBOSE",
            "CONFIDENT_TRACE_ENVIRONMENT",
            "CONFIDENT_TRACE_FLUSH",
            "CONFIDENT_TRACE_SAMPLE_RATE");
    private static final List<String> LLM_FLAGS = List.of(
            "USE_OPENAI_MODEL", "USE_AZURE_OPENAI", "USE_ANTHROPIC_MODEL", "USE_AWS_BEDROCK_MODEL",
            "USE_LOCAL_MODEL", "USE_GROK_MODEL", "USE_MOONSHOT_MODEL", "USE_DEEPSEEK_MODEL",
            "USE_GEMINI_MODEL", "USE_LITELLM", "USE_PORTKEY_MODEL", "USE_OPENROUTER_MODEL");
    private static final List<String> EMBED_FLAGS = List.of("USE_AZURE_OPENAI_EMBEDDING", "USE_LOCAL_EMBEDDINGS");
    private static final List<String> LLM_VALUES = List.of(
            "OPENAI_MODEL_NAME", "AZURE_MODEL_NAME", "AZURE_DEPLOYMENT_NAME", "AZURE_OPENAI_ENDPOINT",
            "OPENAI_API_VERSION", "ANTHROPIC_MODEL_NAME", "AWS_BEDROCK_MODEL_NAME", "AWS_BEDROCK_REGION",
            "OLLAMA_MODEL_NAME", "LOCAL_MODEL_NAME", "LOCAL_MODEL_BASE_URL", "LOCAL_MODEL_FORMAT",
            "GROK_MODEL_NAME", "GROK_BASE_URL", "MOONSHOT_MODEL_NAME", "MOONSHOT_BASE_URL",
            "DEEPSEEK_MODEL_NAME", "GEMINI_MODEL_NAME",
            "GOOGLE_CLOUD_PROJECT", "GOOGLE_CLOUD_LOCATION", "GOOGLE_GENAI_USE_VERTEXAI", "AZURE_MODEL_VERSION",
            "LITELLM_MODEL_NAME", "LITELLM_API_BASE",
            "LITELLM_PROXY_API_BASE", "PORTKEY_MODEL_NAME", "PORTKEY_BASE_URL", "PORTKEY_PROVIDER_NAME",
            "OPENROUTER_MODEL_NAME", "OPENROUTER_BASE_URL", "OPENROUTER_COST_PER_INPUT_TOKEN",
            "OPENROUTER_COST_PER_OUTPUT_TOKEN",
            "ANTHROPIC_COST_PER_INPUT_TOKEN", "ANTHROPIC_COST_PER_OUTPUT_TOKEN",
            "AWS_BEDROCK_COST_PER_INPUT_TOKEN", "AWS_BEDROCK_COST_PER_OUTPUT_TOKEN",
            "GROK_COST_PER_INPUT_TOKEN", "GROK_COST_PER_OUTPUT_TOKEN",
            "MOONSHOT_COST_PER_INPUT_TOKEN", "MOONSHOT_COST_PER_OUTPUT_TOKEN",
            "DEEPSEEK_COST_PER_INPUT_TOKEN", "DEEPSEEK_COST_PER_OUTPUT_TOKEN");
    private static final List<String> EMBED_VALUES = List.of(
            "AZURE_EMBEDDING_MODEL_NAME", "AZURE_EMBEDDING_DEPLOYMENT_NAME",
            "LOCAL_EMBEDDING_MODEL_NAME", "LOCAL_EMBEDDING_BASE_URL");
    private static final java.util.Set<String> KNOWN_SETTING_KEYS = knownSettingKeys();

    private CliSettings() {
    }

    static int settings(String[] args, PrintStream out, PrintStream err) {
        try {
            var parsed = parse(args, 1, err);
            if (parsed == null) {
                return 2;
            }
            if (parsed.listFilters() != null && (!parsed.updates().isEmpty() || !parsed.unsets().isEmpty())) {
                err.println("Cannot use --list with --set or --unset");
                return 2;
            }
            var dotenv = new DotenvFile(parsed.save());
            var updates = new LinkedHashMap<String, String>();
            var explicitUpdates = new java.util.HashSet<String>();
            var removals = new java.util.ArrayList<String>();
            var existing = dotenv.read();
            for (var update : parsed.updates()) {
                var index = update.indexOf('=');
                if (index < 1) {
                    err.println("--set must be KEY=VALUE (got '" + update + "')");
                    return 2;
                }
                var rawKey = update.substring(0, index);
                if (!knownSettingKey(rawKey)) {
                    err.println("Unknown setting: '" + rawKey + "'");
                    return 2;
                }
                var key = settingKey(rawKey);
                if (deprecatedComputedSettingKey(rawKey) && explicitUpdates.contains(key)) {
                    continue;
                }
                try {
                    updates.put(key, settingValue(rawKey, update.substring(index + 1)));
                } catch (IllegalArgumentException error) {
                    err.println(error.getMessage());
                    return 2;
                }
                if (!deprecatedComputedSettingKey(rawKey)) {
                    explicitUpdates.add(key);
                }
            }
            for (var unset : parsed.unsets()) {
                var keys = unsetKeys(existing, unset);
                if (keys.isEmpty()) {
                    err.println("No settings matched");
                    return 2;
                }
                removals.addAll(keys);
            }
            if (!updates.isEmpty() || !removals.isEmpty()) {
                dotenv.update(updates, removals);
            }
            if (parsed.listFilters() != null) {
                if (list(dotenv, parsed.listFilters(), out) == 0) {
                    err.println("No settings matched");
                    return 2;
                }
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
        var optionError = optionError(args, DEBUG_SET_OPTIONS, DEBUG_SET_VALUE_OPTIONS);
        if (optionError != null) {
            err.println(optionError);
            return 2;
        }
        var save = savePath(args);
        var quiet = quiet(args);
        var updates = new LinkedHashMap<String, String>();
        updates.put("LOG_LEVEL", logLevel(option(args, "--log-level", "DEBUG")));
        putToggle(updates, args, "--verbose", "--no-verbose", "DEEPEVAL_VERBOSE_MODE");
        putToggle(updates, args, "--debug-async", "--no-debug-async", "DEEPEVAL_DEBUG_ASYNC");
        putToggle(updates, args, "--log-stack-traces", "--no-log-stack-traces", "DEEPEVAL_LOG_STACK_TRACES");
        putLogLevel(updates, args, "--retry-before-level", "DEEPEVAL_RETRY_BEFORE_LOG_LEVEL");
        putLogLevel(updates, args, "--retry-after-level", "DEEPEVAL_RETRY_AFTER_LOG_LEVEL");
        putToggle(updates, args, "--grpc", "--no-grpc", "DEEPEVAL_GRPC_LOGGING");
        putOption(updates, args, "--grpc-verbosity", "GRPC_VERBOSITY");
        putOption(updates, args, "--grpc-trace", "GRPC_TRACE");
        putToggle(updates, args, "--trace-verbose", "--no-trace-verbose", "CONFIDENT_TRACE_VERBOSE");
        putOption(updates, args, "--trace-env", "CONFIDENT_TRACE_ENVIRONMENT");
        putToggle(updates, args, "--trace-flush", "--no-trace-flush", "CONFIDENT_TRACE_FLUSH");
        putOption(updates, args, "--trace-sample-rate", "CONFIDENT_TRACE_SAMPLE_RATE");
        try {
            new DotenvFile(save).update(updates, List.of());
            if (!quiet) {
                updates.forEach((key, value) -> out.println(key + "=" + value));
            }
            return 0;
        } catch (IOException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    static int unsetDebug(String[] args, PrintStream out, PrintStream err) {
        var optionError = optionError(args, DEBUG_UNSET_OPTIONS, DEBUG_UNSET_VALUE_OPTIONS);
        if (optionError != null) {
            err.println(optionError);
            return 2;
        }
        var save = savePath(args);
        var quiet = quiet(args);
        try {
            new DotenvFile(save).update(Map.of(), DEBUG_KEYS);
            if (!quiet) {
                out.println("Debug settings removed");
            }
            return 0;
        } catch (IOException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static final List<String> DEBUG_SET_OPTIONS = List.of(
            "--log-level",
            "--verbose", "--no-verbose",
            "--debug-async", "--no-debug-async",
            "--log-stack-traces", "--no-log-stack-traces",
            "--retry-before-level", "--retry-after-level",
            "--grpc", "--no-grpc",
            "--grpc-verbosity", "--grpc-trace",
            "--trace-verbose", "--no-trace-verbose",
            "--trace-env",
            "--trace-flush", "--no-trace-flush",
            "--trace-sample-rate",
            "--save", "-s",
            "--quiet", "-q");
    private static final List<String> DEBUG_UNSET_OPTIONS = List.of("--save", "-s", "--quiet", "-q");
    private static final List<String> DEBUG_SET_VALUE_OPTIONS = List.of(
            "--log-level",
            "--retry-before-level", "--retry-after-level",
            "--grpc-verbosity", "--grpc-trace",
            "--trace-env",
            "--trace-sample-rate",
            "--save", "-s");
    private static final List<String> DEBUG_UNSET_VALUE_OPTIONS = List.of("--save", "-s");

    private static String optionError(String[] args, List<String> allowed, List<String> valued) {
        for (var i = 1; i < args.length; i++) {
            var arg = args[i];
            if (!arg.startsWith("-")) {
                continue;
            }
            var name = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
            if (!allowed.contains(name)) {
                return "No such option: " + name;
            }
            if (!arg.contains("=") && valued.contains(name) && missingValue(args, i)) {
                return "Missing value for " + name;
            }
            if ("--trace-sample-rate".equals(name)) {
                var value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : args[i + 1];
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException error) {
                    return "Invalid value for " + name + ": " + value;
                }
            }
        }
        return null;
    }

    static int provider(String command, String[] args, PrintStream err) {
        var spec = ProviderSpec.forCommand(command);
        if (spec == null) {
            return -1;
        }
        var optionError = providerOptionError(command, spec, args);
        if (optionError != null) {
            err.println(optionError);
            return 2;
        }
        try {
            var updates = new LinkedHashMap<String, String>();
            var removals = new java.util.ArrayList<String>();
            removals.addAll(spec.groupFlags());
            removals.addAll(spec.unsetKeys());
            if (command.startsWith("unset-")) {
                removals.add(spec.useKey());
                if (has(args, "--clear-secrets") || has(args, "-x")) {
                    removals.addAll(spec.secretKeys());
                }
            } else {
                updates.put(spec.useKey(), "YES");
                for (var entry : spec.setKeys().entrySet()) {
                    var value = optionValue(args, entry.getKey());
                    if (value != null) {
                        updates.put(entry.getValue(), value);
                    }
                }
                if ("set-openai".equals(command) && !hasValue(savePath(args), updates, "OPENAI_MODEL_NAME")) {
                    err.println("OpenAI model name is not set. Pass --model (or set OPENAI_MODEL_NAME).");
                    return 2;
                }
                if ("set-azure-openai".equals(command) && !hasValue(savePath(args), updates, "AZURE_MODEL_NAME")) {
                    err.println("Azure OpenAI model name is not set. Pass --model (or set AZURE_MODEL_NAME).");
                    return 2;
                }
                if ("set-anthropic".equals(command) && !hasValue(savePath(args), updates, "ANTHROPIC_MODEL_NAME")) {
                    err.println("Anthropic model name is not set. Pass --model (or set ANTHROPIC_MODEL_NAME).");
                    return 2;
                }
                if ("set-bedrock".equals(command) && !hasValue(savePath(args), updates, "AWS_BEDROCK_MODEL_NAME")) {
                    err.println("AWS Bedrock model name is not set. Pass --model (or set AWS_BEDROCK_MODEL_NAME).");
                    return 2;
                }
                if ("set-gemini".equals(command) && !hasValue(savePath(args), updates, "GEMINI_MODEL_NAME")) {
                    err.println("Gemini model name is not set. Pass --model (or set GEMINI_MODEL_NAME).");
                    return 2;
                }
                if ("set-grok".equals(command) && !hasValue(savePath(args), updates, "GROK_MODEL_NAME")) {
                    err.println("Grok model name is not set. Pass --model (or set GROK_MODEL_NAME).");
                    return 2;
                }
                if ("set-moonshot".equals(command) && !hasValue(savePath(args), updates, "MOONSHOT_MODEL_NAME")) {
                    err.println("Moonshot model name is not set. Pass --model (or set MOONSHOT_MODEL_NAME).");
                    return 2;
                }
                if ("set-deepseek".equals(command) && !hasValue(savePath(args), updates, "DEEPSEEK_MODEL_NAME")) {
                    err.println("DeepSeek model name is not set. Pass --model (or set DEEPSEEK_MODEL_NAME).");
                    return 2;
                }
                if ("set-litellm".equals(command) && !hasValue(savePath(args), updates, "LITELLM_MODEL_NAME")) {
                    err.println("LiteLLM model name is not set. Pass --model (or set LITELLM_MODEL_NAME).");
                    return 2;
                }
                if ("set-portkey".equals(command) && !hasValue(savePath(args), updates, "PORTKEY_MODEL_NAME")) {
                    err.println("Portkey model name is not set. Pass --model (or set PORTKEY_MODEL_NAME).");
                    return 2;
                }
                if ("set-openrouter".equals(command) && !hasValue(savePath(args), updates, "OPENROUTER_MODEL_NAME")) {
                    err.println("OpenRouter model name is not set. Pass --model (or set OPENROUTER_MODEL_NAME).");
                    return 2;
                }
                if ("set-ollama".equals(command) && !hasValue(savePath(args), updates, "OLLAMA_MODEL_NAME")) {
                    err.println("Ollama model name is not set. Pass --model (or set OLLAMA_MODEL_NAME).");
                    return 2;
                }
                if ("set-local-model".equals(command) && !hasValue(savePath(args), updates, "LOCAL_MODEL_NAME")) {
                    err.println("Local model name is not set. Pass --model (or set LOCAL_MODEL_NAME).");
                    return 2;
                }
                if ("set-azure-openai-embedding".equals(command)
                        && !hasValue(savePath(args), updates, "AZURE_EMBEDDING_MODEL_NAME")) {
                    err.println("Azure OpenAI embedding model name is not set. Pass --model (or set AZURE_EMBEDDING_MODEL_NAME).");
                    return 2;
                }
                if (("set-local-embeddings".equals(command) || "set-ollama-embeddings".equals(command))
                        && !hasValue(savePath(args), updates, "LOCAL_EMBEDDING_MODEL_NAME")) {
                    err.println("Local embedding model name is not set. Pass --model (or set LOCAL_EMBEDDING_MODEL_NAME).");
                    return 2;
                }
                if ("set-ollama-embeddings".equals(command)) {
                    updates.put("LOCAL_EMBEDDING_API_KEY", "ollama");
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

    static boolean isProviderCommand(String command) {
        return ProviderSpec.forCommand(command) != null;
    }

    private static String providerOptionError(String command, ProviderSpec spec, String[] args) {
        for (var i = 1; i < args.length; i++) {
            var arg = args[i];
            if (!arg.startsWith("-")) {
                continue;
            }
            var name = arg.contains("=") ? arg.substring(0, arg.indexOf('=')) : arg;
            if (!providerOptions(command, spec).contains(name)) {
                return "No such option: " + name;
            }
            if (!arg.contains("=") && providerValuedOptions(command, spec).contains(name) && missingValue(args, i)) {
                return "Missing value for " + name;
            }
            if (numericProviderOption(name)) {
                var value = arg.contains("=") ? arg.substring(arg.indexOf('=') + 1) : args[i + 1];
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException error) {
                    return "Invalid value for " + name + ": " + value;
                }
            }
        }
        return null;
    }

    private static boolean numericProviderOption(String name) {
        return "--cost-per-input-token".equals(name) || "--cost-per-output-token".equals(name)
                || "-i".equals(name) || "-o".equals(name)
                || "--temperature".equals(name) || "-t".equals(name);
    }

    private static List<String> providerOptions(String command, ProviderSpec spec) {
        var options = new java.util.ArrayList<>(providerValuedOptions(command, spec));
        if (command.startsWith("unset-")) {
            options.add("--clear-secrets");
            options.add("-x");
        }
        return options;
    }

    private static List<String> providerValuedOptions(String command, ProviderSpec spec) {
        var options = command.startsWith("unset-")
                ? new java.util.ArrayList<String>()
                : new java.util.ArrayList<>(spec.setKeys().keySet());
        options.add("--save");
        options.add("-s");
        if (!command.startsWith("unset-") && "USE_GEMINI_MODEL".equals(spec.useKey())) {
            options.add("--service-account-file");
        }
        return options;
    }

    private static int list(DotenvFile dotenv, List<String> filters, PrintStream out) throws IOException {
        var needles = filters.stream()
                .map(filter -> filter.toUpperCase(Locale.ROOT).replace("-", "_"))
                .toList();
        var count = new int[1];
        dotenv.read().forEach((key, value) -> {
            if (needles.isEmpty() || needles.stream().anyMatch(key::contains)) {
                out.println(key + "=" + (secret(key) ? "********" : value));
                count[0]++;
            }
        });
        return count[0];
    }

    private static boolean secret(String key) {
        return key.contains("KEY") || key.contains("TOKEN") || key.contains("SECRET");
    }

    private static List<String> unsetKeys(Map<String, String> existing, String filter) {
        var needle = normalizeSettingKey(filter);
        var matches = existing.keySet().stream()
                .filter(key -> key.contains(needle))
                .toList();
        return matches.isEmpty() && existing.containsKey(settingKey(filter)) ? List.of(settingKey(filter)) : matches;
    }

    private static Parsed parse(String[] args, int start, PrintStream err) {
        var updates = new java.util.ArrayList<String>();
        var unsets = new java.util.ArrayList<String>();
        java.util.ArrayList<String> list = null;
        var quiet = quiet(args);
        var save = Path.of(".env");
        for (var i = start; i < args.length; i++) {
            var arg = args[i];
            var equals = arg.indexOf('=');
            if (equals > 0) {
                var value = arg.substring(equals + 1);
                switch (arg.substring(0, equals)) {
                    case "-u", "--set", "--update" -> updates.add(value);
                    case "-U", "--unset" -> unsets.add(value);
                    case "-l", "--list" -> {
                        list = new java.util.ArrayList<>();
                        list.add(value);
                    }
                    case "-s", "--save" -> save = savePath(value);
                    default -> {
                        if (arg.substring(0, equals).startsWith("-")) {
                            err.println("No such option: " + arg.substring(0, equals));
                            return null;
                        }
                    }
                }
                continue;
            }
            switch (arg) {
                case "-u", "--set", "--update" -> {
                    if (missingValue(args, i)) {
                        err.println("Missing value for " + arg);
                        return null;
                    }
                    updates.add(args[++i]);
                }
                case "-U", "--unset" -> {
                    if (missingValue(args, i)) {
                        err.println("Missing value for " + arg);
                        return null;
                    }
                    unsets.add(args[++i]);
                }
                case "-l", "--list" -> {
                    list = new java.util.ArrayList<>();
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        list.add(args[++i]);
                    }
                }
                case "-q", "--quiet" -> quiet = true;
                case "-s", "--save" -> {
                    if (missingValue(args, i)) {
                        err.println("Missing value for " + arg);
                        return null;
                    }
                    save = savePath(args[++i]);
                }
                default -> {
                    if (arg.startsWith("-")) {
                        err.println("No such option: " + arg);
                        return null;
                    }
                    if (list != null) {
                        list.add(arg);
                    }
                }
            }
        }
        return new Parsed(updates, unsets, list, quiet, save);
    }

    private static boolean missingValue(String[] args, int index) {
        return index + 1 == args.length || args[index + 1].startsWith("-");
    }

    private static String settingKey(String key) {
        var normalized = normalizeSettingKey(key);
        return DEPRECATED_COMPUTED_SETTING_KEYS.getOrDefault(normalized, normalized);
    }

    private static boolean deprecatedComputedSettingKey(String key) {
        return DEPRECATED_COMPUTED_SETTING_KEYS.containsKey(normalizeSettingKey(key));
    }

    private static String normalizeSettingKey(String key) {
        return key.strip().toUpperCase(Locale.ROOT).replace("-", "_");
    }

    private static String settingValue(String key, String value) {
        var normalized = settingKey(key);
        if (normalized.equals("LOG_LEVEL")) {
            return logLevel(value);
        }
        if (normalized.equals("DEEPEVAL_RETRY_BEFORE_LOG_LEVEL")
                || normalized.equals("DEEPEVAL_RETRY_AFTER_LOG_LEVEL")) {
            return logLevel(value);
        }
        if (booleanSettingKey(normalized)) {
            return booleanValue(value);
        }
        if (normalized.equals("DEEPEVAL_FILE_SYSTEM")) {
            return fileSystem(value);
        }
        if (normalized.equals("DEEPEVAL_DEFAULT_SAVE")) {
            return defaultSave(value);
        }
        if (normalized.equals("CONFIDENT_REGION")) {
            return value.strip().toUpperCase(Locale.ROOT);
        }
        if (normalized.equals("AWS_BEDROCK_REGION")) {
            return value.strip().toLowerCase(Locale.ROOT);
        }
        if (normalized.equals("TEMPERATURE")) {
            validateDoubleRange(normalized, value, 0.0, 2.0);
        }
        if (normalized.equals("CONFIDENT_TRACE_SAMPLE_RATE")) {
            validateDoubleRange(normalized, value, 0.0, 1.0);
        }
        if (displayLengthSettingKey(normalized)) {
            validateInteger(normalized, value);
        }
        if (normalized.equals("DEEPEVAL_RETRY_MAX_ATTEMPTS")) {
            validateIntegerMin(normalized, value, 1);
        }
        if (normalized.equals("DEEPEVAL_RETRY_EXP_BASE")) {
            validateDoubleRange(normalized, value, 1.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_RETRY_INITIAL_SECONDS")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_RETRY_JITTER")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_RETRY_CAP_SECONDS")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_TIMEOUT_SEMAPHORE_WARN_AFTER_SECONDS")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_TIMEOUT_THREAD_LIMIT")) {
            validateIntegerMin(normalized, value, 1);
        }
        if (normalized.equals("DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE")) {
            validateDoubleRange(normalized, value, Double.MIN_VALUE, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE")) {
            validateDoubleRange(normalized, value, 0.0, Double.POSITIVE_INFINITY);
        }
        if (normalized.equals("DEEPEVAL_MAX_CONCURRENT_DOC_PROCESSING")) {
            validateIntegerMin(normalized, value, 1);
        }
        return value;
    }

    private static void validateInteger(String key, String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
        }
    }

    private static void validateIntegerMin(String key, String value, int min) {
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < min) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
        }
    }

    private static void validateDoubleRange(String key, String value, double min, double max) {
        try {
            var parsed = Double.parseDouble(value);
            if (parsed < min || parsed > max) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Invalid value for " + key + ": " + value);
        }
    }

    private static boolean knownSettingKey(String key) {
        return KNOWN_SETTING_KEYS.contains(normalizeSettingKey(key)) || KNOWN_SETTING_KEYS.contains(settingKey(key));
    }

    private static boolean booleanSettingKey(String key) {
        return LLM_FLAGS.contains(key)
                || EMBED_FLAGS.contains(key)
                || List.of(
                        "CONFIDENT_OPEN_BROWSER",
                        "CONFIDENT_TRACE_FLUSH",
                        "CONFIDENT_TRACE_INTERNAL",
                        "CONFIDENT_TRACE_VERBOSE",
                        "CUDA_LAUNCH_BLOCKING",
                        "DEEPEVAL_DEBUG_ASYNC",
                        "DEEPEVAL_DISABLE_DOTENV",
                        "DEEPEVAL_DISABLE_TIMEOUTS",
                        "DEEPEVAL_GRPC_LOGGING",
                        "DEEPEVAL_LOG_STACK_TRACES",
                        "DEEPEVAL_TELEMETRY_OPT_OUT",
                        "DEEPEVAL_UPDATE_WARNING_OPT_IN",
                        "DEEPEVAL_VERBOSE_MODE",
                        "ENABLE_DEEPEVAL_CACHE",
                        "ERROR_REPORTING",
                        "GOOGLE_GENAI_USE_VERTEXAI",
                        "IGNORE_DEEPEVAL_ERRORS",
                        "SKIP_DEEPEVAL_MISSING_PARAMS",
                        "TOKENIZERS_PARALLELISM",
                        "TRANSFORMERS_NO_ADVISORY_WARNINGS")
                        .contains(key);
    }

    private static boolean displayLengthSettingKey(String key) {
        return List.of(
                "DEEPEVAL_MAXLEN_TINY",
                "DEEPEVAL_MAXLEN_SHORT",
                "DEEPEVAL_MAXLEN_MEDIUM",
                "DEEPEVAL_MAXLEN_LONG",
                "DEEPEVAL_SHORTEN_DEFAULT_MAXLEN")
                .contains(key);
    }

    private static java.util.Set<String> knownSettingKeys() {
        var keys = new java.util.HashSet<String>();
        keys.addAll(DEBUG_KEYS);
        keys.addAll(LLM_FLAGS);
        keys.addAll(LLM_VALUES);
        keys.addAll(EMBED_FLAGS);
        keys.addAll(EMBED_VALUES);
        keys.addAll(DEPRECATED_COMPUTED_SETTING_KEYS.keySet());
        keys.addAll(DEPRECATED_COMPUTED_SETTING_KEYS.values());
        keys.addAll(List.of(
                "ANTHROPIC_API_KEY", "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY",
                "AZURE_OPENAI_API_KEY", "DEEPSEEK_API_KEY", "GOOGLE_API_KEY",
                "GOOGLE_SERVICE_ACCOUNT_KEY", "GROK_API_KEY", "LITELLM_API_KEY",
                "LITELLM_PROXY_API_KEY", "LOCAL_EMBEDDING_API_KEY", "LOCAL_MODEL_API_KEY",
                "MAX_TOKENS", "MOONSHOT_API_KEY", "OPENAI_API_KEY", "OPENROUTER_API_KEY",
                "PORTKEY_API_KEY", "TEMPERATURE", "CONFIDENT_OPEN_BROWSER", "CONFIDENT_REGION",
                "CONFIDENT_TRACE_INTERNAL",
                "CUDA_LAUNCH_BLOCKING", "CUDA_VISIBLE_DEVICES",
                "DEEPEVAL_DEFAULT_SAVE",
                "DEEPEVAL_DISABLE_DOTENV", "DEEPEVAL_DISABLE_TIMEOUTS", "DEEPEVAL_FILE_SYSTEM",
                "DEEPEVAL_MAXLEN_TINY", "DEEPEVAL_MAXLEN_SHORT",
                "DEEPEVAL_MAXLEN_MEDIUM", "DEEPEVAL_MAXLEN_LONG",
                "DEEPEVAL_RESULTS_FOLDER",
                "DEEPEVAL_RETRY_CAP_SECONDS", "DEEPEVAL_RETRY_EXP_BASE",
                "DEEPEVAL_RETRY_INITIAL_SECONDS", "DEEPEVAL_RETRY_JITTER",
                "DEEPEVAL_RETRY_MAX_ATTEMPTS", "DEEPEVAL_SDK_RETRY_PROVIDERS",
                "DEEPEVAL_TELEMETRY_OPT_OUT", "DEEPEVAL_UPDATE_WARNING_OPT_IN",
                "ENABLE_DEEPEVAL_CACHE", "ERROR_REPORTING",
                "IGNORE_DEEPEVAL_ERRORS", "SKIP_DEEPEVAL_MISSING_PARAMS",
                "DEEPEVAL_MAX_CONCURRENT_DOC_PROCESSING",
                "DEEPEVAL_SHORTEN_DEFAULT_MAXLEN", "DEEPEVAL_SHORTEN_SUFFIX",
                "DEEPEVAL_TIMEOUT_SEMAPHORE_WARN_AFTER_SECONDS", "DEEPEVAL_TIMEOUT_THREAD_LIMIT",
                "PYTHONPATH", "TOKENIZERS_PARALLELISM", "TRANSFORMERS_NO_ADVISORY_WARNINGS"));
        return keys;
    }

    private static void putOption(Map<String, String> updates, String[] args, String option, String key) {
        var value = optionValue(args, option);
        if (value != null) {
            updates.put(key, value);
        }
    }

    private static void putLogLevel(Map<String, String> updates, String[] args, String option, String key) {
        var value = optionValue(args, option);
        if (value != null) {
            updates.put(key, logLevel(value));
        }
    }

    private static void putToggle(
            Map<String, String> updates, String[] args, String enabledOption, String disabledOption, String key) {
        if (has(args, disabledOption)) {
            updates.put(key, "false");
            return;
        }
        var value = optionValue(args, enabledOption);
        if (value != null) {
            updates.put(key, value);
        } else if (has(args, enabledOption)) {
            updates.put(key, "true");
        }
    }

    private static String logLevel(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "DEBUG" -> "10";
            case "INFO" -> "20";
            case "WARNING", "WARN" -> "30";
            case "ERROR" -> "40";
            case "CRITICAL" -> "50";
            case "NOTSET" -> "0";
            default -> value;
        };
    }

    private static String booleanValue(String value) {
        return switch (value.strip().replace("\"", "").replace("'", "").toLowerCase(Locale.ROOT)) {
            case "1", "true", "t", "yes", "y", "on", "enable", "enabled" -> "true";
            case "0", "false", "f", "no", "n", "off", "disable", "disabled" -> "false";
            default -> "false";
        };
    }

    private static String defaultSave(String value) {
        var trimmed = value.strip();
        if (trimmed.equals("dotenv") || trimmed.equals("dotenv:")) {
            return "dotenv";
        }
        if (trimmed.startsWith("dotenv:")) {
            return trimmed;
        }
        throw new IllegalArgumentException("Invalid value for DEEPEVAL_DEFAULT_SAVE: " + value);
    }

    private static String fileSystem(String value) {
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "READ_ONLY", "READ-ONLY", "READONLY", "RO" -> "READ_ONLY";
            default -> throw new IllegalArgumentException("Invalid value for DEEPEVAL_FILE_SYSTEM: " + value);
        };
    }

    private static Path savePath(String[] args) {
        for (var i = 0; i < args.length - 1; i++) {
            if ("--save".equals(args[i]) || "-s".equals(args[i])) {
                return savePath(args[i + 1]);
            }
        }
        for (var arg : args) {
            if (arg.startsWith("--save=")) {
                return savePath(arg.substring("--save=".length()));
            }
            if (arg.startsWith("-s=")) {
                return savePath(arg.substring("-s=".length()));
            }
        }
        return Path.of(".env");
    }

    private static Path savePath(String value) {
        if ("dotenv".equals(value) || "dotenv:".equals(value)) {
            return Path.of(".env.local");
        }
        return value.startsWith("dotenv:") ? Path.of(value.substring("dotenv:".length())) : Path.of(value);
    }

    private static String optionValue(String[] args, String name) {
        for (var i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i]) && !args[i + 1].startsWith("--")) {
                return args[i + 1];
            }
        }
        var prefix = name + "=";
        for (var arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
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

    private static boolean quiet(String[] args) {
        return has(args, "--quiet") || has(args, "-q");
    }

    private static boolean hasValue(Path save, Map<String, String> updates, String key) throws IOException {
        var value = updates.get(key);
        if (value != null && !value.isBlank()) {
            return true;
        }
        value = new DotenvFile(save).read().get(key);
        return value != null && !value.isBlank();
    }

    private record Parsed(List<String> updates, List<String> unsets, List<String> listFilters, boolean quiet, Path save) {
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
                        Map.of("--model", "ANTHROPIC_MODEL_NAME", "-m", "ANTHROPIC_MODEL_NAME",
                                "--cost-per-input-token", "ANTHROPIC_COST_PER_INPUT_TOKEN",
                                "-i", "ANTHROPIC_COST_PER_INPUT_TOKEN",
                                "--cost-per-output-token", "ANTHROPIC_COST_PER_OUTPUT_TOKEN",
                                "-o", "ANTHROPIC_COST_PER_OUTPUT_TOKEN"),
                        "ANTHROPIC_API_KEY");
                case "set-ollama", "unset-ollama" -> llmWithSecrets(
                        "USE_LOCAL_MODEL",
                        Map.of("--model", "OLLAMA_MODEL_NAME", "-m", "OLLAMA_MODEL_NAME",
                                "--base-url", "LOCAL_MODEL_BASE_URL", "-u", "LOCAL_MODEL_BASE_URL"),
                        "LOCAL_MODEL_API_KEY");
                case "set-local-model", "unset-local-model" -> llmWithSecrets(
                        "USE_LOCAL_MODEL",
                        Map.of("--model", "LOCAL_MODEL_NAME", "-m", "LOCAL_MODEL_NAME",
                                "--base-url", "LOCAL_MODEL_BASE_URL", "-u", "LOCAL_MODEL_BASE_URL",
                                "--format", "LOCAL_MODEL_FORMAT", "-f", "LOCAL_MODEL_FORMAT"),
                        "LOCAL_MODEL_API_KEY");
                case "set-azure-openai", "unset-azure-openai" -> llmWithSecrets(
                        "USE_AZURE_OPENAI",
                        Map.of("--model", "AZURE_MODEL_NAME", "-m", "AZURE_MODEL_NAME",
                                "--deployment-name", "AZURE_DEPLOYMENT_NAME", "-d", "AZURE_DEPLOYMENT_NAME",
                                "--base-url", "AZURE_OPENAI_ENDPOINT", "-u", "AZURE_OPENAI_ENDPOINT",
                                "--api-version", "OPENAI_API_VERSION", "-v", "OPENAI_API_VERSION",
                                "--model-version", "AZURE_MODEL_VERSION", "-V", "AZURE_MODEL_VERSION"),
                        "AZURE_OPENAI_API_KEY");
                case "set-bedrock", "unset-bedrock" -> llmWithSecrets(
                        "USE_AWS_BEDROCK_MODEL",
                        Map.of("--model", "AWS_BEDROCK_MODEL_NAME", "-m", "AWS_BEDROCK_MODEL_NAME",
                                "--region", "AWS_BEDROCK_REGION", "-r", "AWS_BEDROCK_REGION",
                                "--cost-per-input-token", "AWS_BEDROCK_COST_PER_INPUT_TOKEN",
                                "-i", "AWS_BEDROCK_COST_PER_INPUT_TOKEN",
                                "--cost-per-output-token", "AWS_BEDROCK_COST_PER_OUTPUT_TOKEN",
                                "-o", "AWS_BEDROCK_COST_PER_OUTPUT_TOKEN"),
                        "AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY");
                case "set-grok", "unset-grok" -> llmWithSecrets(
                        "USE_GROK_MODEL",
                        Map.of("--model", "GROK_MODEL_NAME", "-m", "GROK_MODEL_NAME",
                                "--base-url", "GROK_BASE_URL", "-u", "GROK_BASE_URL",
                                "--cost-per-input-token", "GROK_COST_PER_INPUT_TOKEN",
                                "-i", "GROK_COST_PER_INPUT_TOKEN",
                                "--cost-per-output-token", "GROK_COST_PER_OUTPUT_TOKEN",
                                "-o", "GROK_COST_PER_OUTPUT_TOKEN"),
                        "GROK_API_KEY");
                case "set-moonshot", "unset-moonshot" -> llmWithSecrets(
                        "USE_MOONSHOT_MODEL",
                        Map.of("--model", "MOONSHOT_MODEL_NAME", "-m", "MOONSHOT_MODEL_NAME",
                                "--base-url", "MOONSHOT_BASE_URL", "-u", "MOONSHOT_BASE_URL",
                                "--cost-per-input-token", "MOONSHOT_COST_PER_INPUT_TOKEN",
                                "-i", "MOONSHOT_COST_PER_INPUT_TOKEN",
                                "--cost-per-output-token", "MOONSHOT_COST_PER_OUTPUT_TOKEN",
                                "-o", "MOONSHOT_COST_PER_OUTPUT_TOKEN"),
                        "MOONSHOT_API_KEY");
                case "set-deepseek", "unset-deepseek" -> llmWithSecrets(
                        "USE_DEEPSEEK_MODEL",
                        Map.of("--model", "DEEPSEEK_MODEL_NAME", "-m", "DEEPSEEK_MODEL_NAME",
                                "--cost-per-input-token", "DEEPSEEK_COST_PER_INPUT_TOKEN",
                                "-i", "DEEPSEEK_COST_PER_INPUT_TOKEN",
                                "--cost-per-output-token", "DEEPSEEK_COST_PER_OUTPUT_TOKEN",
                                "-o", "DEEPSEEK_COST_PER_OUTPUT_TOKEN"),
                        "DEEPSEEK_API_KEY");
                case "set-gemini", "unset-gemini" -> gemini();
                case "set-litellm", "unset-litellm" -> llmWithSecrets(
                        "USE_LITELLM",
                        Map.of("--model", "LITELLM_MODEL_NAME", "-m", "LITELLM_MODEL_NAME",
                                "--base-url", "LITELLM_API_BASE", "-u", "LITELLM_API_BASE",
                                "--proxy-base-url", "LITELLM_PROXY_API_BASE",
                                "-U", "LITELLM_PROXY_API_BASE"),
                        "LITELLM_API_KEY", "LITELLM_PROXY_API_KEY");
                case "set-portkey", "unset-portkey" -> llmWithSecrets(
                        "USE_PORTKEY_MODEL",
                        Map.of("--model", "PORTKEY_MODEL_NAME", "-m", "PORTKEY_MODEL_NAME",
                                "--base-url", "PORTKEY_BASE_URL", "-u", "PORTKEY_BASE_URL",
                                "--provider", "PORTKEY_PROVIDER_NAME", "-P", "PORTKEY_PROVIDER_NAME"),
                        "PORTKEY_API_KEY");
                case "set-openrouter", "unset-openrouter" -> openRouter();
                case "set-azure-openai-embedding", "unset-azure-openai-embedding" -> embed(
                        "USE_AZURE_OPENAI_EMBEDDING",
                        Map.of("--model", "AZURE_EMBEDDING_MODEL_NAME",
                                "-m", "AZURE_EMBEDDING_MODEL_NAME",
                                "--deployment-name", "AZURE_EMBEDDING_DEPLOYMENT_NAME",
                                "-d", "AZURE_EMBEDDING_DEPLOYMENT_NAME"));
                case "set-local-embeddings", "unset-local-embeddings",
                        "set-ollama-embeddings", "unset-ollama-embeddings" -> embed(
                                "USE_LOCAL_EMBEDDINGS",
                                Map.of("--model", "LOCAL_EMBEDDING_MODEL_NAME",
                                        "-m", "LOCAL_EMBEDDING_MODEL_NAME",
                                        "--base-url", "LOCAL_EMBEDDING_BASE_URL",
                                        "-u", "LOCAL_EMBEDDING_BASE_URL"),
                                List.of("LOCAL_EMBEDDING_API_KEY"));
                default -> null;
            };
        }

        private static ProviderSpec openAi() {
            var setKeys = Map.of(
                    "--model", "OPENAI_MODEL_NAME",
                    "-m", "OPENAI_MODEL_NAME",
                    "--temperature", "TEMPERATURE",
                    "--cost-per-input-token", "OPENAI_COST_PER_INPUT_TOKEN",
                    "-i", "OPENAI_COST_PER_INPUT_TOKEN",
                    "--cost-per-output-token", "OPENAI_COST_PER_OUTPUT_TOKEN",
                    "-o", "OPENAI_COST_PER_OUTPUT_TOKEN");
            return llm("USE_OPENAI_MODEL", setKeys, List.of(
                    "OPENAI_MODEL_NAME",
                    "OPENAI_COST_PER_INPUT_TOKEN",
                    "OPENAI_COST_PER_OUTPUT_TOKEN"), List.of("OPENAI_API_KEY"));
        }

        private static ProviderSpec openRouter() {
            var setKeys = Map.of(
                    "--model", "OPENROUTER_MODEL_NAME",
                    "-m", "OPENROUTER_MODEL_NAME",
                    "--base-url", "OPENROUTER_BASE_URL",
                    "-u", "OPENROUTER_BASE_URL",
                    "--temperature", "TEMPERATURE",
                    "-t", "TEMPERATURE",
                    "--cost-per-input-token", "OPENROUTER_COST_PER_INPUT_TOKEN",
                    "-i", "OPENROUTER_COST_PER_INPUT_TOKEN",
                    "--cost-per-output-token", "OPENROUTER_COST_PER_OUTPUT_TOKEN",
                    "-o", "OPENROUTER_COST_PER_OUTPUT_TOKEN");
            return llm("USE_OPENROUTER_MODEL", setKeys, List.of(
                    "OPENROUTER_MODEL_NAME",
                    "OPENROUTER_BASE_URL",
                    "OPENROUTER_COST_PER_INPUT_TOKEN",
                    "OPENROUTER_COST_PER_OUTPUT_TOKEN"), List.of("OPENROUTER_API_KEY"));
        }

        private static ProviderSpec gemini() {
            var setKeys = Map.of(
                    "--model", "GEMINI_MODEL_NAME",
                    "-m", "GEMINI_MODEL_NAME",
                    "--project", "GOOGLE_CLOUD_PROJECT",
                    "-p", "GOOGLE_CLOUD_PROJECT",
                    "--location", "GOOGLE_CLOUD_LOCATION",
                    "-l", "GOOGLE_CLOUD_LOCATION");
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
