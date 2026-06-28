package dev.jeval.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JEvalCliTest {
    @TempDir
    Path tempDir;

    @Test
    void rejectsMissingFileWithDeepEvalStyleMessage() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", tempDir.resolve("missing.json").toString()}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("neither a valid file nor a directory"));
    }

    @Test
    void exitsNonZeroWhenEvaluationFails() throws Exception {
        var file = tempDir.resolve("judgment_eval.json");
        Files.writeString(file, """
                {
                  "name": "cli",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", file.toString()}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("JEval Evaluation Results"));
        assertTrue(text(out).contains("failed=1"));
    }

    @Test
    void writesRequestedReportFormatToOutputDirectory() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "html-report",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var output = tempDir.resolve("reports");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", file.toString(), "--format", "html", "--output", output.toString()},
                out, err);

        assertEquals(0, exit);
        assertTrue(Files.readString(output.resolve("html-report.html")).contains("JEval Evaluation Results"));
    }

    @Test
    void quietSuppressesConsoleReportButStillRunsDirectory() throws Exception {
        var dir = tempDir.resolve("cases");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("one.json"), """
                {
                  "name": "one",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", dir.toString(), "--quiet"}, out, err);

        assertEquals(0, exit);
        assertEquals("", text(out));
    }

    private static PrintStream print(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private int run(String[] args, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return JEvalCli.run(args, print(out), print(err), tempDir);
    }

    private static String text(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
