package dev.jeval.cli;

import dev.jeval.report.EvaluationReportWriter;
import dev.jeval.runner.TestRunner;
import dev.jeval.store.LocalRunStore;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class JEvalCli {
    private JEvalCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, out, err, Path.of(""));
    }

    static int run(String[] args, PrintStream out, PrintStream err, Path storeRoot) {
        if (args.length < 2 || !"test".equals(args[0])) {
            usage(err);
            return 2;
        }
        var path = Path.of(args[1]);
        if (!Files.isRegularFile(path) && !Files.isDirectory(path)) {
            err.println(path + " is neither a valid file nor a directory");
            return 2;
        }
        var options = options(args, err);
        if (options == null) {
            return 2;
        }
        try {
            var result = new TestRunner().run(path);
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

    private static Options options(String[] args, PrintStream err) {
        var format = "markdown";
        Path output = null;
        var quiet = false;
        for (var i = 2; i < args.length; i++) {
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
        return new Options(format, output, quiet);
    }

    private static String report(dev.jeval.runner.TestRunResult result, String format) {
        return format.equals("html") ? EvaluationReportWriter.html(result) : EvaluationReportWriter.markdown(result);
    }

    private static String fileName(String name, String format) {
        return name.replaceAll("[^A-Za-z0-9._-]+", "-") + (format.equals("html") ? ".html" : ".md");
    }

    private static void usage(PrintStream err) {
        err.println("Usage: jeval test <file-or-directory> [--format markdown|html] [--output dir] [--quiet]");
    }

    private record Options(String format, Path output, boolean quiet) {
    }
}
