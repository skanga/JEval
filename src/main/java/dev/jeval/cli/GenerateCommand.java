package dev.jeval.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationDataset;
import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.Utils;
import dev.jeval.synthesizer.ConversationalStylingConfig;
import dev.jeval.synthesizer.EvolutionConfig;
import dev.jeval.synthesizer.StylingConfig;
import dev.jeval.synthesizer.Synthesizer;
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
            var dataset = new EvaluationDataset(goldens);
            var file = dataset.saveAs(option(args, "--file-type", "json"),
                    Path.of(option(args, "--output-dir", "synthetic_data")),
                    fileName);
            out.println(file);
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
                ? LangChain4jProviderModels.from(new DotenvFile(savePath(args)))
                : new ScriptedModel(Files.readAllLines(Path.of(responses)).stream()
                        .filter(line -> !line.isBlank())
                        .toList());
        return new Synthesizer(model, stylingConfig(args), conversationalStylingConfig(args), new EvolutionConfig());
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
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        for (var path : paths) {
            for (var file : documentFiles(path)) {
                addDocumentContexts(args, contexts, sourceFiles, file);
            }
        }
        return synthesizer.generateGoldensFromContexts(contexts,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                sourceFiles);
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
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        for (var path : paths) {
            for (var file : documentFiles(path)) {
                addDocumentContexts(args, contexts, sourceFiles, file);
            }
        }
        return synthesizer.generateConversationalGoldensFromContexts(contexts,
                includeExpected(args),
                integer(args, "--max-goldens-per-context", 2),
                sourceFiles);
    }

    private static void addDocumentContexts(
            String[] args,
            List<List<String>> contexts,
            List<String> sourceFiles,
            Path file) throws IOException {
        var maxContexts = integer(args, "--max-contexts-per-document", 3);
        var minContexts = integer(args, "--min-contexts-per-document", 1);
        var chunkSize = integer(args, "--chunk-size", 1024);
        var chunkOverlap = integer(args, "--chunk-overlap", 0);
        validateChunkOverlap(chunkSize, chunkOverlap);
        var chunks = Utils.chunkText(Files.readString(file), chunkSize, chunkOverlap);
        validateMinContexts(chunks.size(), minContexts);
        var count = 0;
        for (var chunk : chunks) {
            if (count++ >= maxContexts) {
                break;
            }
            contexts.add(List.of(chunk));
            sourceFiles.add(file.getFileName().toString());
        }
    }

    private static void validateChunkOverlap(int chunkSize, int chunkOverlap) {
        if (chunkOverlap > chunkSize - 1) {
            throw new IllegalArgumentException(
                    "`chunk_overlap` must not exceed " + (chunkSize - 1) + " (chunk_size - 1).");
        }
    }

    private static void validateMinContexts(int numChunks, int minContexts) {
        if (numChunks >= minContexts) {
            return;
        }
        var message = new StringBuilder()
                .append("Impossible to generate ")
                .append(minContexts)
                .append(" contexts from a document with ")
                .append(numChunks)
                .append(" chunks.\nYou have the following options:");
        if (numChunks > 0) {
            message.append("\n1. Adjust the `min_contexts_per_document` to no more than ")
                    .append(numChunks)
                    .append(".");
        }
        throw new IllegalArgumentException(message.toString());
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

    private static List<Path> documentFiles(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        try (var files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static String option(String[] args, String name, String fallback) {
        for (var i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
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

    private static boolean includeExpected(String[] args) {
        return !has(args, "--no-expected-output") && !has(args, "--no-include-expected");
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

        private ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            if (prompts.size() > responses.size()) {
                throw new IllegalArgumentException("No scripted response for prompt " + prompts.size());
            }
            return responses.get(prompts.size() - 1);
        }
    }
}
