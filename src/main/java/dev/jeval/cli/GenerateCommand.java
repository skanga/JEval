package dev.jeval.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.EvaluationDataset;
import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.Utils;
import dev.jeval.synthesizer.StylingConfig;
import dev.jeval.synthesizer.Synthesizer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GenerateCommand {
    private static final ObjectMapper JSON = new ObjectMapper();

    private GenerateCommand() {
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        var method = option(args, "--method", null);
        var variation = option(args, "--variation", "single-turn");
        if (!"single-turn".equals(variation)) {
            err.println("Only --variation single-turn is implemented in JEval CLI generate.");
            return 2;
        }
        if ("contexts".equals(method) && option(args, "--contexts-file", null) == null) {
            err.println("--contexts-file is required for --method contexts");
            return 2;
        }
        if ("scratch".equals(method) && option(args, "--scenario", null) == null) {
            err.println("--scenario is required for --method scratch");
            return 2;
        }
        if ("goldens".equals(method) && option(args, "--goldens-file", null) == null) {
            err.println("--goldens-file is required for --method goldens");
            return 2;
        }
        if ("docs".equals(method) && option(args, "--document-path", null) == null) {
            err.println("--document-path is required for --method docs");
            return 2;
        }
        try {
            var synthesizer = synthesizer(args);
            var goldens = switch (method == null ? "" : method) {
                case "contexts" -> fromContexts(args, synthesizer, err);
                case "scratch" -> fromScratch(args, synthesizer, err);
                case "goldens" -> fromGoldens(args, synthesizer, err);
                case "docs" -> fromDocs(args, synthesizer, err);
                default -> {
                    err.println("Missing or unsupported --method.");
                    yield null;
                }
            };
            if (goldens == null) {
                return 2;
            }
            var dataset = new EvaluationDataset(goldens);
            var file = dataset.saveAs(option(args, "--file-type", "json"),
                    Path.of(option(args, "--output-dir", ".")),
                    option(args, "--file-name", "generated"));
            out.println(file);
            return 0;
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static Synthesizer synthesizer(String[] args) throws IOException {
        var responses = option(args, "--responses-file", null);
        var model = responses == null
                ? LangChain4jProviderModels.from(new DotenvFile(savePath(args)))
                : new ScriptedModel(Files.readAllLines(Path.of(responses)).stream()
                        .filter(line -> !line.isBlank())
                        .toList());
        var scenario = option(args, "--scenario", null);
        if (scenario == null) {
            return new Synthesizer(model);
        }
        return new Synthesizer(model, new StylingConfig(
                scenario,
                option(args, "--task", ""),
                option(args, "--input-format", ""),
                option(args, "--expected-output-format", null)));
    }

    private static List<Golden> fromContexts(String[] args, Synthesizer synthesizer, PrintStream err) throws IOException {
        var file = option(args, "--contexts-file", null);
        if (file == null) {
            err.println("--contexts-file is required for --method contexts");
            return null;
        }
        var contexts = JSON.readValue(Path.of(file).toFile(), new TypeReference<List<List<String>>>() {});
        return synthesizer.generateGoldensFromContexts(contexts,
                !has(args, "--no-expected-output"),
                integer(args, "--max-goldens-per-context", 2),
                null);
    }

    private static List<Golden> fromScratch(String[] args, Synthesizer synthesizer, PrintStream err) {
        if (option(args, "--scenario", null) == null) {
            err.println("--scenario is required for --method scratch");
            return null;
        }
        return synthesizer.generateGoldensFromScratch(integer(args, "--num-goldens", 1));
    }

    private static List<Golden> fromGoldens(String[] args, Synthesizer synthesizer, PrintStream err) {
        var file = option(args, "--goldens-file", null);
        if (file == null) {
            err.println("--goldens-file is required for --method goldens");
            return null;
        }
        var dataset = new EvaluationDataset();
        dataset.addGoldensFromJsonFile(Path.of(file));
        return synthesizer.generateGoldensFromGoldens(
                dataset.goldens(),
                integer(args, "--max-goldens-per-golden", 1),
                !has(args, "--no-expected-output"));
    }

    private static List<Golden> fromDocs(String[] args, Synthesizer synthesizer, PrintStream err) throws IOException {
        var path = option(args, "--document-path", null);
        if (path == null) {
            err.println("--document-path is required for --method docs");
            return null;
        }
        var contexts = new ArrayList<List<String>>();
        var sourceFiles = new ArrayList<String>();
        for (var file : documentFiles(Path.of(path))) {
            for (var chunk : Utils.chunkText(Files.readString(file), integer(args, "--chunk-size", 20))) {
                contexts.add(List.of(chunk));
                sourceFiles.add(file.getFileName().toString());
            }
        }
        return synthesizer.generateGoldensFromContexts(contexts,
                !has(args, "--no-expected-output"),
                integer(args, "--max-goldens-per-context", 2),
                sourceFiles);
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

    private static Path savePath(String[] args) {
        var save = option(args, "--save", ".env");
        return save.startsWith("dotenv:") ? Path.of(save.substring("dotenv:".length())) : Path.of(save);
    }

    private static int integer(String[] args, String name, int fallback) {
        return Integer.parseInt(option(args, name, Integer.toString(fallback)));
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
