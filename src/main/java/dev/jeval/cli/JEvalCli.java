package dev.jeval.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.report.EvaluationReportWriter;
import dev.jeval.runner.TestRunner;
import dev.jeval.runner.TestRunResult;
import dev.jeval.store.LocalRunStore;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;

public final class JEvalCli {
    private static final ObjectMapper JSON = new ObjectMapper();

    private JEvalCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, Path.of(""));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Path storeRoot) {
        if (args.length > 0 && "settings".equals(args[0])) {
            return CliSettings.settings(args, out, err);
        }
        if (args.length > 0 && "set-debug".equals(args[0])) {
            return CliSettings.setDebug(args, out, err);
        }
        if (args.length > 0 && "unset-debug".equals(args[0])) {
            return CliSettings.unsetDebug(args, out, err);
        }
        if (args.length > 0) {
            var providerExit = CliSettings.provider(args[0], args, err);
            if (providerExit >= 0) {
                return providerExit;
            }
        }
        if (args.length > 0 && "generate".equals(args[0])) {
            return GenerateCommand.run(args, out, err);
        }
        if (args.length > 0 && "inspect".equals(args[0])) {
            return inspect(args, out, err, storeRoot);
        }
        if (args.length < 2 || !"test".equals(args[0])) {
            usage(err);
            return 2;
        }
        var pathIndex = args.length > 1 && "run".equals(args[1]) ? 2 : 1;
        if (args.length <= pathIndex) {
            usage(err);
            return 2;
        }
        var target = target(args[pathIndex]);
        if (!Files.isRegularFile(target.path()) && !Files.isDirectory(target.path())) {
            err.println(target.path() + " is neither a valid file nor a directory");
            return 2;
        }
        var options = options(args, pathIndex + 1, err);
        if (options == null) {
            return 2;
        }
        try {
            var result = new TestRunner().run(target.path(), target.selector());
            if (options.identifier() != null) {
                result = withName(result, options.identifier());
            }
            new LocalRunStore(storeRoot).write(result);
            var report = report(result, options.format());
            if (options.output() != null) {
                Files.createDirectories(options.output());
                Files.writeString(options.output().resolve(fileName(result.name(), options.format())), report);
            }
            if (!options.quiet()) {
                out.print(report);
            }
            return result.success() ? 0 : 1;
        } catch (IOException | IllegalArgumentException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static int inspect(String[] args, PrintStream out, PrintStream err, Path storeRoot) {
        var options = inspectOptions(args, err);
        if (options == null) {
            return 2;
        }
        try {
            var target = inspectTarget(options, storeRoot);
            if (target == null) {
                err.println("No test_run_*.json file found. Run an eval first, or pass a path or folder.");
                return 2;
            }
            var result = JSON.readValue(target.toFile(), TestRunResult.class);
            out.print(report(result, options.format()));
            return 0;
        } catch (IOException | IllegalArgumentException error) {
            err.println(error.getMessage());
            return 2;
        }
    }

    private static InspectOptions inspectOptions(String[] args, PrintStream err) {
        var format = "markdown";
        Path path = null;
        Path folder = null;
        for (var i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--format" -> {
                    if (++i == args.length) {
                        usage(err);
                        return null;
                    }
                    format = args[i].toLowerCase(Locale.ROOT);
                }
                case "-f", "--folder" -> {
                    if (++i == args.length) {
                        usage(err);
                        return null;
                    }
                    folder = Path.of(args[i]);
                }
                default -> {
                    if (path != null) {
                        usage(err);
                        return null;
                    }
                    path = Path.of(args[i]);
                }
            }
        }
        if (!format.equals("markdown") && !format.equals("html")) {
            err.println("Unsupported format: " + format);
            return null;
        }
        return new InspectOptions(path, folder, format);
    }

    private static Path inspectTarget(InspectOptions options, Path storeRoot) throws IOException {
        if (options.path() != null) {
            if (Files.isRegularFile(options.path())) {
                return options.path();
            }
            if (Files.isDirectory(options.path())) {
                return latestTimestampedRun(options.path());
            }
            throw new IllegalArgumentException("Path not found: " + options.path());
        }
        if (options.folder() != null) {
            return Files.isDirectory(options.folder()) ? latestTimestampedRun(options.folder()) : null;
        }
        var rolling = storeRoot.resolve(".deepeval").resolve(".latest_run_full.json");
        return Files.isRegularFile(rolling) ? rolling : null;
    }

    private static Path latestTimestampedRun(Path folder) throws IOException {
        try (var files = Files.list(folder)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("test_run_\\d{8}_\\d{6}(?:_\\d+)?\\.json"))
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path);
                        } catch (IOException error) {
                            throw new IllegalStateException(error);
                        }
                    }))
                    .orElse(null);
        } catch (IllegalStateException error) {
            if (error.getCause() instanceof IOException ioError) {
                throw ioError;
            }
            throw error;
        }
    }

    private static Options options(String[] args, int start, PrintStream err) {
        var format = "markdown";
        Path output = null;
        var quiet = false;
        String identifier = null;
        for (var i = start; i < args.length; i++) {
            switch (args[i]) {
                case "--quiet" -> quiet = true;
                case "--format" -> {
                    if (++i == args.length) {
                        usage(err);
                        return null;
                    }
                    format = args[i].toLowerCase(Locale.ROOT);
                }
                case "--output" -> {
                    if (++i == args.length) {
                        usage(err);
                        return null;
                    }
                    output = Path.of(args[i]);
                }
                case "-id", "--identifier" -> {
                    if (++i == args.length) {
                        usage(err);
                        return null;
                    }
                    identifier = args[i];
                }
                default -> {
                    usage(err);
                    return null;
                }
            }
        }
        if (!format.equals("markdown") && !format.equals("html")) {
            err.println("Unsupported format: " + format);
            return null;
        }
        return new Options(format, output, quiet, identifier);
    }

    private static String report(dev.jeval.runner.TestRunResult result, String format) {
        return format.equals("html") ? EvaluationReportWriter.html(result) : EvaluationReportWriter.markdown(result);
    }

    private static TestRunResult withName(TestRunResult result, String name) {
        return new TestRunResult(name, result.results(), result.summary(), result.aggregates());
    }

    private static String fileName(String name, String format) {
        return name.replaceAll("[^A-Za-z0-9._-]+", "-") + (format.equals("html") ? ".html" : ".md");
    }

    private static TestTarget target(String value) {
        var selectorStart = value.indexOf("::");
        if (selectorStart < 0) {
            return new TestTarget(Path.of(value), null);
        }
        return new TestTarget(Path.of(value.substring(0, selectorStart)), value.substring(selectorStart + 2));
    }

    private static void usage(PrintStream err) {
        err.println("Usage: jeval test [run] <file-or-directory> [-id|--identifier name] [--format markdown|html] [--output dir] [--quiet]");
        err.println("       jeval inspect [test-run-file-or-directory] [--folder dir] [--format markdown|html]");
        err.println("       jeval settings -u key=value|-U key|-l [filter] [-s|--save dotenv:.env] [-q|--quiet]");
        err.println("       jeval set-debug [--log-level level] [--verbose|--no-verbose] [-s|--save dotenv:.env] [-q|--quiet]");
        err.println("       jeval unset-debug [-s|--save dotenv:.env] [-q|--quiet]");
        err.println("       jeval set-openai|set-ollama|set-anthropic ... [-s|--save dotenv:.env]");
        err.println("       jeval generate --method contexts|scratch|goldens --variation single-turn [-s|--save dotenv:.env] ...");
    }

    private record Options(String format, Path output, boolean quiet, String identifier) {
    }

    private record InspectOptions(Path path, Path folder, String format) {
    }

    private record TestTarget(Path path, String selector) {
    }
}
