package dev.jeval.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationDataset;
import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.synthesizer.ConversationalStylingConfig;
import dev.jeval.synthesizer.ContextConstructionConfig;
import dev.jeval.synthesizer.EvolutionConfig;
import dev.jeval.synthesizer.StylingConfig;
import dev.jeval.synthesizer.Synthesizer;
import dev.jeval.synthesizer.SynthesizerOptions;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class GenerateCommand {
    private static final ObjectMapper JSON = new ObjectMapper();

    private GenerateCommand() {
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        var method = lowerOption(args, "--method", null);
        var variation = lowerOption(args, "--variation", null);
        if (method == null) {
            err.println("--method is required");
            return 2;
        }
        if (!supportedMethod(method)) {
            err.println("Missing or unsupported --method.");
            return 2;
        }
        if (variation == null) {
            err.println("--variation is required");
            return 2;
        }
        if (!"single-turn".equals(variation) && !"multi-turn".equals(variation)) {
            err.println("Only --variation single-turn and multi-turn are implemented in JEval CLI generate.");
            return 2;
        }
        var fileType = lowerOption(args, "--file-type", "json");
        if (!supportedFileType(fileType)) {
            err.println("Invalid file type. Available file types to save as: json, csv, jsonl");
            return 2;
        }
        if ("contexts".equals(method) && option(args, "--contexts-file", null) == null) {
            err.println("--contexts-file is required for --method contexts");
            return 2;
        }
        if ("scratch".equals(method)) {
            var scratchValidation = validateScratch(args, variation);
            if (scratchValidation != null) {
                err.println(scratchValidation);
                return 2;
            }
        }
        if ("goldens".equals(method) && option(args, "--goldens-file", null) == null) {
            err.println("--goldens-file is required for --method goldens");
            return 2;
        }
        if ("docs".equals(method) && documentPaths(args).isEmpty()) {
            err.println("--document-path or --documents is required for --method docs");
            return 2;
        }
        try {
            if ("docs".equals(method)) {
                contextConstructionConfig(args);
            }
            var synthesizer = synthesizer(args);
            var goldens = "multi-turn".equals(variation)
                    ? multiTurnGoldens(method, args, synthesizer, err)
                    : singleTurnGoldens(method, args, synthesizer, err);
            if (goldens == null) {
                return 2;
            }
            var fileName = option(args, "--file-name", null);
            if (fileName != null && fileName.contains(".")) {
                err.println("file_name should not contain periods or file extensions. "
                        + "The file extension will be added based on the file_type parameter.");
                return 2;
            }
            var file = synthesizer.saveAs(option(args, "--file-type", "json"),
                    Path.of(option(args, "--output-dir", "synthetic_data")),
                    fileName,
                    true);
            out.println("Synthetic goldens saved at " + file + "!");
            return 0;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static List<?> singleTurnGoldens(
            String method,
            String[] args,
            Synthesizer synthesizer,
            PrintStream err) throws IOException {
        return switch (method == null ? "" : method) {
            case "contexts" -> fromContexts(args, synthesizer, err);
            case "scratch" -> fromScratch(args, synthesizer, err);
            case "goldens" -> fromGoldens(args, synthesizer, err);
            case "docs" -> fromDocs(args, synthesizer, err);
            default -> {
                err.println("Missing or unsupported --method.");
                yield null;
            }
        };
    }

    private static List<?> multiTurnGoldens(
            String method,
            String[] args,
            Synthesizer synthesizer,
            PrintStream err) throws IOException {
        return switch (method == null ? "" : method) {
            case "contexts" -> fromConversationalContexts(args, synthesizer, err);
            case "scratch" -> synthesizer.generateConversationalGoldensFromScratch(integer(args, "--num-goldens", 1));
            case "docs" -> fromConversationalDocs(args, synthesizer, err);
            case "goldens" -> fromConversationalGoldens(args, synthesizer, err);
            default -> {
                err.println("Missing or unsupported --method for --variation multi-turn.");
                yield null;
            }
        };
    }

    private static Synthesizer synthesizer(String[] args) throws IOException {
        var responses = option(args, "--responses-file", null);
        var model = responses == null
                ? LangChain4jProviderModels.from(new DotenvFile(savePath(args)), option(args, "--model", null))
                : new ScriptedModel(Files.readAllLines(Path.of(responses)).stream()
                        .filter(line -> !line.isBlank())
                        .toList());
        return new Synthesizer(
                model,
                stylingConfig(args),
                conversationalStylingConfig(args),
                new EvolutionConfig(),
                synthesizerOptions(args));
    }

    private static SynthesizerOptions synthesizerOptions(String[] args) {
        return new SynthesizerOptions(
                booleanPair(args, "--async-mode", "--sync-mode", true),
                integer(args, "--max-concurrent", 100),
                has(args, "--cost-tracking"));
    }

    private static StylingConfig stylingConfig(String[] args) {
        var scenario = option(args, "--scenario", null);
        var task = option(args, "--task", null);
        var inputFormat = option(args, "--input-format", null);
        var expectedOutputFormat = option(args, "--expected-output-format", null);
        if (!any(scenario, task, inputFormat, expectedOutputFormat)) {
            return null;
        }
        return new StylingConfig(scenario, task, inputFormat, expectedOutputFormat);
    }

    private static ConversationalStylingConfig conversationalStylingConfig(String[] args) {
        var scenarioContext = option(args, "--scenario-context", null);
        var conversationalTask = option(args, "--conversational-task", null);
        var participantRoles = option(args, "--participant-roles", null);
        var scenarioFormat = option(args, "--scenario-format", null);
        var expectedOutcomeFormat = option(args, "--expected-outcome-format", null);
        if (!any(scenarioContext, conversationalTask, participantRoles, scenarioFormat, expectedOutcomeFormat)) {
            return null;
        }
        return new ConversationalStylingConfig(
                scenarioContext,
                conversationalTask,
                participantRoles,
                scenarioFormat,
                expectedOutcomeFormat);
    }

    private static List<Golden> fromContexts(String[] args, Synthesizer synthesizer, PrintStream err) throws IOException {
        var file = option(args, "--contexts-file", null);
        if (file == null) {
            err.println("--contexts-file is required for --method contexts");
            return null;
        }
        var contexts = loadContexts(Path.of(file));
        return synthesizer.generateGoldensFromContexts(contexts,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                null);
    }

    private static List<ConversationalGolden> fromConversationalContexts(
            String[] args,
            Synthesizer synthesizer,
            PrintStream err) throws IOException {
        var file = option(args, "--contexts-file", null);
        if (file == null) {
            err.println("--contexts-file is required for --method contexts");
            return null;
        }
        var contexts = loadContexts(Path.of(file));
        return synthesizer.generateConversationalGoldensFromContexts(contexts,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                null);
    }

    private static List<Golden> fromScratch(String[] args, Synthesizer synthesizer, PrintStream err) {
        return synthesizer.generateGoldensFromScratch(integer(args, "--num-goldens", 1));
    }

    private static List<List<String>> loadContexts(Path file) throws IOException {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Contexts file not found: " + file);
        }
        JsonNode root;
        try {
            root = JSON.readTree(file.toFile());
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Contexts file must be valid JSON: " + error.getOriginalMessage(), error);
        }
        if (!root.isArray()) {
            throw new IllegalArgumentException("Contexts file must contain a JSON list of context lists.");
        }
        var contexts = new ArrayList<List<String>>();
        for (var context : root) {
            if (!context.isArray()) {
                throw new IllegalArgumentException(
                        "Contexts file must be shaped like [[\"chunk 1\", \"chunk 2\"], ...].");
            }
            var chunks = new ArrayList<String>();
            for (var chunk : context) {
                if (!chunk.isTextual()) {
                    throw new IllegalArgumentException(
                            "Contexts file must be shaped like [[\"chunk 1\", \"chunk 2\"], ...].");
                }
                chunks.add(chunk.asText());
            }
            contexts.add(List.copyOf(chunks));
        }
        return List.copyOf(contexts);
    }

    private static List<Golden> fromGoldens(String[] args, Synthesizer synthesizer, PrintStream err) {
        var file = option(args, "--goldens-file", null);
        if (file == null) {
            err.println("--goldens-file is required for --method goldens");
            return null;
        }
        var dataset = new EvaluationDataset();
        loadGoldens(dataset, Path.of(file));
        validateGoldensVariation(dataset, false);
        return synthesizer.generateGoldensFromGoldens(
                dataset.goldens(),
                integer(args, "--max-goldens-per-golden", 2),
                includeExpected(args));
    }

    private static List<ConversationalGolden> fromConversationalGoldens(
            String[] args,
            Synthesizer synthesizer,
            PrintStream err) {
        var file = option(args, "--goldens-file", null);
        if (file == null) {
            err.println("--goldens-file is required for --method goldens");
            return null;
        }
        var dataset = new EvaluationDataset();
        loadGoldens(dataset, Path.of(file));
        validateGoldensVariation(dataset, true);
        return synthesizer.generateConversationalGoldensFromGoldens(
                dataset.conversationalGoldens(),
                integer(args, "--max-goldens-per-golden", 2),
                includeExpected(args));
    }

    private static List<Golden> fromDocs(String[] args, Synthesizer synthesizer, PrintStream err) throws IOException {
        var paths = documentPaths(args);
        if (paths.isEmpty()) {
            err.println("--document-path or --documents is required for --method docs");
            return null;
        }
        return synthesizer.generateGoldensFromDocs(paths,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                contextConstructionConfig(args));
    }

    private static List<ConversationalGolden> fromConversationalDocs(
            String[] args,
            Synthesizer synthesizer,
            PrintStream err) throws IOException {
        var paths = documentPaths(args);
        if (paths.isEmpty()) {
            err.println("--document-path or --documents is required for --method docs");
            return null;
        }
        return synthesizer.generateConversationalGoldensFromDocs(paths,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                contextConstructionConfig(args));
    }

    private static ContextConstructionConfig contextConstructionConfig(String[] args) {
        return new ContextConstructionConfig(
                integer(args, "--max-contexts-per-document", ContextConstructionConfig.DEFAULT.maxContextsPerDocument()),
                integer(args, "--min-contexts-per-document", ContextConstructionConfig.DEFAULT.minContextsPerDocument()),
                integer(args, "--chunk-size", ContextConstructionConfig.DEFAULT.chunkSize()),
                integer(args, "--chunk-overlap", ContextConstructionConfig.DEFAULT.chunkOverlap()),
                decimal(args, "--context-quality-threshold", ContextConstructionConfig.DEFAULT.contextQualityThreshold()),
                decimal(args, "--context-similarity-threshold", ContextConstructionConfig.DEFAULT.contextSimilarityThreshold()),
                integer(args, "--max-retries", ContextConstructionConfig.DEFAULT.maxRetries()),
                has(args, "--allow-cross-file-contexts"),
                optionalInteger(args, "--target-files-per-context"),
                integer(args, "--max-files-per-context", ContextConstructionConfig.DEFAULT.maxFilesPerContext()));
    }

    private static void loadGoldens(EvaluationDataset dataset, Path file) {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Goldens file not found: " + file);
        }
        var name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jsonl")) {
            dataset.addGoldensFromJsonlFile(file);
        } else if (name.endsWith(".csv")) {
            dataset.addGoldensFromCsvFile(file);
        } else if (name.endsWith(".json")) {
            dataset.addGoldensFromJsonFile(file);
        } else {
            throw new IllegalArgumentException("Goldens file must be a .json, .csv, or .jsonl file.");
        }
    }

    private static void validateGoldensVariation(EvaluationDataset dataset, boolean multiTurn) {
        if (dataset.goldens().isEmpty() && dataset.conversationalGoldens().isEmpty()) {
            throw new IllegalArgumentException("Goldens file does not contain any goldens.");
        }
        if (multiTurn && dataset.conversationalGoldens().isEmpty()) {
            throw new IllegalArgumentException("`--variation multi-turn` requires conversational goldens.");
        }
        if (!multiTurn && dataset.goldens().isEmpty()) {
            throw new IllegalArgumentException("`--variation single-turn` requires single-turn goldens.");
        }
    }

    private static String option(String[] args, String name, String fallback) {
        for (var i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        var prefix = name + "=";
        for (var arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }

    private static String lowerOption(String[] args, String name, String fallback) {
        var value = option(args, name, fallback);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static Path savePath(String[] args) {
        var save = ".env";
        for (var i = 0; i < args.length - 1; i++) {
            if ("--save".equals(args[i]) || "-s".equals(args[i])) {
                save = args[i + 1];
                break;
            }
        }
        for (var arg : args) {
            if (arg.startsWith("--save=")) {
                save = arg.substring("--save=".length());
            } else if (arg.startsWith("-s=")) {
                save = arg.substring("-s=".length());
            }
        }
        return save.startsWith("dotenv:") ? Path.of(save.substring("dotenv:".length())) : Path.of(save);
    }

    private static int integer(String[] args, String name, int fallback) {
        return Integer.parseInt(option(args, name, Integer.toString(fallback)));
    }

    private static Integer optionalInteger(String[] args, String name) {
        var value = option(args, name, null);
        return value == null ? null : Integer.parseInt(value);
    }

    private static double decimal(String[] args, String name, double fallback) {
        return Double.parseDouble(option(args, name, Double.toString(fallback)));
    }

    private static boolean includeExpected(String[] args) {
        var value = true;
        for (var arg : args) {
            if ("--include-expected".equals(arg)) {
                value = true;
            } else if ("--no-include-expected".equals(arg) || "--no-expected-output".equals(arg)) {
                value = false;
            }
        }
        return value;
    }

    private static boolean booleanPair(String[] args, String positiveName, String negativeName, boolean fallback) {
        var value = fallback;
        for (var arg : args) {
            if (positiveName.equals(arg)) {
                value = true;
            } else if (negativeName.equals(arg)) {
                value = false;
            }
        }
        return value;
    }

    private static List<Path> documentPaths(String[] args) {
        var paths = new ArrayList<Path>();
        var legacyPath = option(args, "--document-path", null);
        if (legacyPath != null) {
            paths.add(Path.of(legacyPath));
        }
        for (var i = 0; i < args.length - 1; i++) {
            if ("--documents".equals(args[i])) {
                paths.add(Path.of(args[i + 1]));
            }
        }
        for (var arg : args) {
            if (arg.startsWith("--documents=")) {
                paths.add(Path.of(arg.substring("--documents=".length())));
            }
        }
        return List.copyOf(paths);
    }

    private static String validateScratch(String[] args, String variation) {
        if (option(args, "--num-goldens", null) == null) {
            return "`--num-goldens` is required when --method is `scratch`.";
        }
        var missing = new ArrayList<String>();
        if ("single-turn".equals(variation)) {
            addMissing(missing, args, "--scenario");
            addMissing(missing, args, "--task");
            addMissing(missing, args, "--input-format");
        } else {
            addMissing(missing, args, "--scenario-context");
            addMissing(missing, args, "--conversational-task");
            addMissing(missing, args, "--participant-roles");
        }
        return missing.isEmpty() ? null : "Scratch generation requires: " + String.join(", ", missing);
    }

    private static void addMissing(List<String> missing, String[] args, String option) {
        if (option(args, option, null) == null) {
            missing.add(option);
        }
    }

    private static boolean any(String... values) {
        for (var value : values) {
            if (value != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean supportedMethod(String method) {
        return "contexts".equals(method)
                || "scratch".equals(method)
                || "goldens".equals(method)
                || "docs".equals(method);
    }

    private static boolean supportedFileType(String fileType) {
        return "json".equals(fileType) || "jsonl".equals(fileType) || "csv".equals(fileType);
    }

    private static boolean has(String[] args, String name) {
        for (var arg : args) {
            if (name.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private int nextResponse;

        private ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public synchronized String generate(String prompt) {
            prompts.add(prompt);
            if (isSyntheticInputEvaluationPrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("\"score\""))) {
                return "{\"feedback\":\"The synthetic query is clear.\",\"score\":1.0}";
            }
            if (isSyntheticScenarioEvaluationPrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("\"score\""))) {
                return "{\"feedback\":\"The conversational scenario is clear.\",\"score\":1.0}";
            }
            if (isPromptStructureExtractionPrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("\"scenario\""))) {
                return "{\"scenario\":\"Example users\",\"task\":\"Answer the user\",\"input_format\":\"One user request\"}";
            }
            if (isRewritePrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("rewritten_input"))) {
                return "{\"rewritten_input\":\"" + escapeJson(rewriteInput(prompt)) + "\"}";
            }
            if (isEvolvedInputStylePrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("\"input\""))) {
                return "{\"input\":\"" + escapeJson(styledInput(prompt)) + "\"}";
            }
            if (isScenarioEvolutionPrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("rewritten_scenario"))) {
                return "{\"rewritten_scenario\":\"" + escapeJson(rewriteScenario(prompt)) + "\"}";
            }
            if (isEvolvedScenarioStylePrompt(prompt)
                    && (nextResponse >= responses.size() || !responses.get(nextResponse).contains("\"scenario\""))) {
                return "{\"scenario\":\"" + escapeJson(styledScenario(prompt)) + "\"}";
            }
            if (isConversationalExpectedOutcomePrompt(prompt)
                    && (nextResponse >= responses.size() || responses.get(nextResponse).contains("\"data\""))) {
                return "Generated conversational expected outcome";
            }
            if (isExpectedOutputPrompt(prompt)
                    && (nextResponse >= responses.size() || responses.get(nextResponse).contains("\"data\""))) {
                return "Generated expected output";
            }
            if (nextResponse >= responses.size()) {
                throw new IllegalArgumentException("No scripted response for prompt " + prompts.size()
                        + ": " + prompt.lines().findFirst().orElse(prompt));
            }
            return responses.get(nextResponse++);
        }
    }

    private static boolean isRewritePrompt(String prompt) {
        return prompt.startsWith("Rewrite the input using this evolution:");
    }

    private static boolean isScenarioEvolutionPrompt(String prompt) {
        return prompt.startsWith("Rewrite the conversational scenario using this evolution:");
    }

    private static boolean isEvolvedInputStylePrompt(String prompt) {
        return prompt.startsWith("Given the evolved input");
    }

    private static boolean isEvolvedScenarioStylePrompt(String prompt) {
        return prompt.startsWith("Given the evolved conversational scenario");
    }

    private static boolean isSyntheticInputEvaluationPrompt(String prompt) {
        return prompt.startsWith("Evaluate the provided synthetic query");
    }

    private static boolean isSyntheticScenarioEvaluationPrompt(String prompt) {
        return prompt.startsWith("Evaluate the provided conversational scenario");
    }

    private static boolean isPromptStructureExtractionPrompt(String prompt) {
        return prompt.startsWith("Analyze the following user inputs and infer the common prompt structure");
    }

    private static boolean isConversationalExpectedOutcomePrompt(String prompt) {
        return prompt.startsWith("Generate the expected outcome for this conversation scenario");
    }

    private static boolean isExpectedOutputPrompt(String prompt) {
        return prompt.startsWith("Generate the expected output for the input");
    }

    private static String rewriteInput(String prompt) {
        var marker = "Input:";
        var index = prompt.lastIndexOf(marker);
        return index < 0 ? "" : prompt.substring(index + marker.length()).trim();
    }

    private static String rewriteScenario(String prompt) {
        var marker = "Scenario:";
        var contextMarker = "Context:";
        var index = prompt.lastIndexOf(marker);
        if (index < 0) {
            return "";
        }
        var start = index + marker.length();
        var end = prompt.indexOf(contextMarker, start);
        return (end < 0 ? prompt.substring(start) : prompt.substring(start, end)).trim();
    }

    private static String styledInput(String prompt) {
        var marker = "Evolved Input:";
        var scenarioMarker = "Scenario:";
        var index = prompt.indexOf(marker);
        if (index < 0) {
            return "";
        }
        var start = index + marker.length();
        var end = prompt.indexOf(scenarioMarker, start);
        return (end < 0 ? prompt.substring(start) : prompt.substring(start, end)).trim();
    }

    private static String styledScenario(String prompt) {
        var marker = "Evolved Scenario:";
        var rolesMarker = "Participant Roles:";
        var index = prompt.indexOf(marker);
        if (index < 0) {
            return "";
        }
        var start = index + marker.length();
        var end = prompt.indexOf(rolesMarker, start);
        return (end < 0 ? prompt.substring(start) : prompt.substring(start, end)).trim();
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
