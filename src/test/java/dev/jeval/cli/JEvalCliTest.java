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
    void testRunSubcommandUsesDeepEvalStyleEntrypointAndIdentifier() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "spec-name",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var output = tempDir.resolve("reports");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "test", "run", file.toString(), "--identifier", "release-smoke",
                "--format", "markdown", "--output", output.toString()
        }, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("release-smoke"));
        assertTrue(Files.readString(output.resolve("release-smoke.md")).contains("release-smoke"));
        assertTrue(Files.readString(tempDir.resolve(".jeval").resolve(".jeval")).contains("\"name\" : \"release-smoke\""));
    }

    @Test
    void testRunSupportsDeepEvalIdentifierAlias() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "spec-name",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var output = tempDir.resolve("reports");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "test", "run", file.toString(), "-id", "release-smoke",
                "--format", "markdown", "--output", output.toString()
        }, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("release-smoke"));
        assertTrue(Files.readString(output.resolve("release-smoke.md")).contains("release-smoke"));
    }

    @Test
    void testRunRepeatRunsCasesMultipleTimes() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "repeat-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--repeat", "3"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("total=3"));
        assertTrue(text(out).contains("passed=3"));
    }

    @Test
    void testRunRepeatAliasRequiresAtLeastOneRun() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "repeat-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-r", "0"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("repeat argument must be at least 1"));
    }

    @Test
    void testRunExitOnFirstFailureStopsAfterFirstFailedCase() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "exit-first-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"},
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--exit-on-first-failure"}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("total=1"));
        assertTrue(text(out).contains("failed=1"));
        assertEquals(false, text(out).contains("good"));
    }

    @Test
    void testRunExitOnFirstFailureSupportsShortAlias() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "exit-first-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-x"}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("total=2"));
        assertTrue(text(out).contains("failed=1"));
    }

    @Test
    void testRunExitOnFirstFailureNegativeAliasIsAccepted() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "exit-first-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"},
                    {"name": "also-bad", "input": "q", "actualOutput": "a", "expectedOutput": "c"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-X"}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("total=2"));
        assertTrue(text(out).contains("### also-bad"));
    }

    @Test
    void testRunDisplayFiltersReportedCases() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "display-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--display", "failing"}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("Summary: total=2"));
        assertEquals(false, text(out).contains("### good"));
        assertTrue(text(out).contains("### bad"));

        out.reset();
        err.reset();
        exit = run(new String[] {"test", "run", file.toString(), "-d", "passing"}, out, err);

        assertEquals(1, exit);
        assertTrue(text(out).contains("Summary: total=2"));
        assertTrue(text(out).contains("### good"));
        assertEquals(false, text(out).contains("### bad"));
    }

    @Test
    void testRunIgnoreErrorsRecordsCaseErrorAndContinues() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "ignore-errors-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a"},
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--ignore-errors"}, out, err);

        assertEquals(1, exit, text(err));
        assertTrue(text(out).contains("Summary: total=2 passed=1 failed=1"));
        assertTrue(text(out).contains("### bad"));
        assertTrue(text(out).contains("Evaluation error:"));
        assertTrue(text(out).contains("### good"));
    }

    @Test
    void testRunIgnoreErrorsSupportsShortAlias() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "ignore-errors-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-i"}, out, err);

        assertEquals(1, exit, text(err));
        assertTrue(text(out).contains("Summary: total=1 passed=0 failed=1"));
        assertEquals("", text(err));
    }

    @Test
    void testRunShowWarningsNegativeAliasIsAccepted() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "warnings-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-W", "--quiet"}, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
        assertEquals("", text(err));
    }

    @Test
    void testRunSkipOnMissingParamsSkipsIncompleteCases() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "skip-missing-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a"},
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--skip-on-missing-params"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=1 passed=1 failed=0"));
        assertEquals(false, text(out).contains("### bad"));
        assertTrue(text(out).contains("### good"));
    }

    @Test
    void testRunSkipOnMissingParamsSupportsShortAlias() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "skip-missing-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "bad", "input": "q", "actualOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-s"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=0 passed=0 failed=0"));
        assertEquals("", text(err));
    }

    @Test
    void testRunMarkFiltersCasesByTag() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "mark-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "smoke", "tags": ["smoke"], "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "slow", "tags": ["slow"], "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--mark", "smoke"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=1 passed=1 failed=0"));
        assertTrue(text(out).contains("### smoke"));
        assertEquals(false, text(out).contains("### slow"));
    }

    @Test
    void testRunMarkAliasRejectsUnmatchedTag() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "mark-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "smoke", "tags": ["smoke"], "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-m", "regression"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No test case matched mark: regression"));
    }

    @Test
    void testRunOfficialWarnsAndContinuesLocally() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "official-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--official"}, out, err);

        assertEquals(0, exit);
        assertTrue(text(out).contains("Summary: total=1 passed=1 failed=0"));
        assertTrue(text(err).contains("Warning: --official is not supported by local JEval runs. Skipping."));
    }

    @Test
    void testRunOfficialSupportsShortAlias() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "official-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-o", "--quiet"}, out, err);

        assertEquals(0, exit);
        assertEquals("", text(out));
        assertTrue(text(err).contains("Warning: --official is not supported by local JEval runs. Skipping."));
    }

    @Test
    void testRunAcceptsPytestCompatibilityOptionsLocally() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "pytest-compat-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "test", "run", file.toString(),
                "--color", "no",
                "--durations", "5",
                "--pdb",
                "--show-warnings",
                "--num-processes", "2"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=1 passed=1 failed=0"));
        assertEquals("", text(err));
    }

    @Test
    void testRunAcceptsPytestCompatibilityAliasesLocally() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "pytest-compat-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-w", "-n", "2", "--quiet"}, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
        assertEquals("", text(err));
    }

    @Test
    void testRunUseCacheReadsCachedMetricResults() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "cache-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var cache = tempDir.resolve(".deepeval").resolve(".deepeval-cache.json");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"test", "run", file.toString(), "--quiet"}, out, err), text(err));
        assertTrue(Files.readString(cache).contains("Exact Match"));
        Files.writeString(cache, Files.readString(cache)
                .replace("The actual and expected outputs are exact matches.", "Loaded from local cache."));

        out.reset();
        err.reset();
        var exit = run(new String[] {"test", "run", file.toString(), "--use-cache"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Loaded from local cache."));
    }

    @Test
    void testRunUseCacheAliasIsDisabledForRepeat() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "cache-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var cache = tempDir.resolve(".deepeval").resolve(".deepeval-cache.json");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"test", "run", file.toString(), "--quiet"}, out, err), text(err));
        Files.writeString(cache, Files.readString(cache)
                .replace("The actual and expected outputs are exact matches.", "Loaded from local cache."));

        out.reset();
        err.reset();
        var exit = run(new String[] {"test", "run", file.toString(), "-c", "--repeat", "2"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=2 passed=2 failed=0"));
        assertEquals(false, text(out).contains("Loaded from local cache."));
    }

    @Test
    void testRunVerboseFlagIsAccepted() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "verbose-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--verbose"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("Summary: total=1 passed=1 failed=0"));
    }

    @Test
    void testRunVerboseShortAliasIsAccepted() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "verbose-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "-v", "--quiet"}, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
    }

    @Test
    void testRunSelectorRunsOnlyMatchingNamedCase() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "selector-spec",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "bad", "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file + "::good"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("total=1"));
        assertTrue(text(out).contains("passed=1"));
        assertEquals(false, text(out).contains("bad"));
    }

    @Test
    void inspectPrintsLatestDeepEvalStyleRun() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "inspectable",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        assertEquals(0, run(new String[] {"test", "run", file.toString(), "--quiet"}, out, err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"inspect"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("JEval Evaluation Results"));
        assertTrue(text(out).contains("inspectable"));
        assertTrue(text(out).contains("passed=1"));
    }

    @Test
    void inspectFolderUsesLatestTimestampedRunFile() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "folder-inspect",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        assertEquals(0, run(new String[] {"test", "run", file.toString(), "--quiet"}, out, err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"inspect", tempDir.resolve(".deepeval").toString()}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("folder-inspect"));
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

    @Test
    void generateCommandRequiresResponsesFileOrProviderSettings() {
        var env = tempDir.resolve("missing.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "users", "--task", "answer", "--input-format", "question",
                "--num-goldens", "1", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No supported provider"));
    }

    @Test
    void generateAcceptsSaveEqualsDotenvFormForProviderSettings() throws Exception {
        withDefaultDotenv("USE_OPENAI_MODEL=YES\n", () -> {
            var env = tempDir.resolve("missing.env");
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            var exit = run(new String[] {"generate", "--method", "scratch", "--variation", "single-turn",
                    "--scenario", "users", "--task", "answer", "--input-format", "question",
                    "--num-goldens", "1", "--save=dotenv:" + env}, out, err);

            assertEquals(2, exit);
            assertTrue(text(err).contains("No supported provider"));
        });
    }

    @Test
    void generateAcceptsShortSaveAliasForProviderSettings() throws Exception {
        withDefaultDotenv("USE_OPENAI_MODEL=YES\n", () -> {
            var env = tempDir.resolve("missing.env");
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            var exit = run(new String[] {"generate", "--method", "scratch", "--variation", "single-turn",
                    "--scenario", "users", "--task", "answer", "--input-format", "question",
                    "--num-goldens", "1", "-s", "dotenv:" + env}, out, err);

            assertEquals(2, exit);
            assertTrue(text(err).contains("No supported provider"));
        });
    }

    @Test
    void generateContextsWritesGoldensFile() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "generated"
        }, out, err);

        assertEquals(0, exit);
        var generated = Files.readString(output.resolve("generated.json"));
        assertTrue(generated.contains("\"input\" : \"Capital?\""));
        assertTrue(generated.contains("\"expected_output\" : \"Paris\""));
    }

    @Test
    void generateAcceptsCaseInsensitiveMethodAndVariationLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "CONTEXTS", "--variation", "SINGLE-TURN",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "case-insensitive"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("case-insensitive.json"));
        assertTrue(generated.contains("\"input\" : \"Capital?\""));
    }

    @Test
    void generateUsesDeepEvalDefaultOutputDirectoryAndTimestampedFileName() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        try {
            var exit = run(new String[] {
                    "generate", "--method", "contexts", "--variation", "single-turn",
                    "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
            }, out, err);

            assertEquals(0, exit, text(err));
            var generatedPath = Path.of(text(out).trim());
            assertEquals(Path.of("synthetic_data"), generatedPath.getParent());
            assertTrue(generatedPath.getFileName().toString().matches("\\d{8}_\\d{6}\\.json"));
            assertTrue(Files.readString(generatedPath).contains("\"input\" : \"Capital?\""));
        } finally {
            Files.deleteIfExists(Path.of("generated.json"));
            try (var files = Files.exists(Path.of("synthetic_data"))
                    ? Files.walk(Path.of("synthetic_data"))
                    : java.util.stream.Stream.<Path>empty()) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (java.io.IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void generateRejectsFileNameWithExtensionLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "goldens.json"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("file_name should not contain periods or file extensions"));
        assertEquals(false, Files.exists(output.resolve("goldens.json.json")));
    }

    @Test
    void generateSupportsDeepEvalNoIncludeExpectedAlias() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--no-include-expected", "--output-dir", output.toString(), "--file-name", "without-expected"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("without-expected.json"));
        assertTrue(generated.contains("Capital?"));
        assertTrue(generated.contains("\"expected_output\" : null"));
    }

    @Test
    void generateScratchWritesGoldensFile() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--task", "study", "--input-format", "question",
                "--num-goldens", "1", "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "scratch"
        }, out, err);

        assertEquals(0, exit);
        assertTrue(Files.readString(output.resolve("scratch.json")).contains("Study question?"));
    }

    @Test
    void generateScratchRequiresNumGoldensLikeDeepEval() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--task", "study", "--input-format", "question",
                "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--num-goldens"));
    }

    @Test
    void generateScratchRequiresSingleTurnStylingLikeDeepEval() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--num-goldens", "1", "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Scratch generation requires: --task, --input-format"));
    }

    @Test
    void generateScratchRequiresMultiTurnStylingLikeDeepEval() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"scenario\":\"flight booking\",\"turns\":[{\"role\":\"user\",\"content\":\"Book a flight\"}]}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "multi-turn",
                "--scenario-context", "travel support", "--num-goldens", "1",
                "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Scratch generation requires: --conversational-task, --participant-roles"));
    }

    @Test
    void generateGoldensAcceptsJsonlSourceFile() throws Exception {
        var goldens = tempDir.resolve("goldens.jsonl");
        Files.writeString(goldens, "{\"input\":\"Old question?\",\"expected_output\":\"Old answer\"}");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"New question?\",\"expected_output\":\"New answer\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "from-jsonl"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("from-jsonl.json"));
        assertTrue(generated.contains("\"input\" : \"New question?\""));
        assertTrue(generated.contains("\"expected_output\" : \"New answer\""));
    }

    @Test
    void generateGoldensAcceptsCsvSourceFile() throws Exception {
        var goldens = tempDir.resolve("goldens.csv");
        Files.writeString(goldens, """
                input,expected_output
                Old question?,Old answer
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Csv question?\",\"expected_output\":\"Csv answer\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "from-csv"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("from-csv.json"));
        assertTrue(generated.contains("\"input\" : \"Csv question?\""));
        assertTrue(generated.contains("\"expected_output\" : \"Csv answer\""));
    }

    @Test
    void generateGoldensReportsMissingGoldensFileLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("missing-goldens.json");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"New question?\",\"expected_output\":\"New answer\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Goldens file not found: " + goldens));
    }

    @Test
    void generateGoldensRejectsUnsupportedFileTypeLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("goldens.txt");
        Files.writeString(goldens, "input,expected_output\nOld question?,Old answer\n");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"New question?\",\"expected_output\":\"New answer\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Goldens file must be a .json, .csv, or .jsonl file."));
    }

    @Test
    void generateGoldensRejectsSingleTurnFileForMultiTurnVariationLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("single-turn-goldens.json");
        Files.writeString(goldens, """
                [
                  {"input":"Old question?","expected_output":"Old answer"}
                ]
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"scenario\":\"flight change\",\"turns\":[{\"role\":\"user\",\"content\":\"Change flight\"}]}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "multi-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("`--variation multi-turn` requires conversational goldens."));
    }

    @Test
    void generateGoldensRejectsConversationalFileForSingleTurnVariationLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("conversation-goldens.json");
        Files.writeString(goldens, """
                [
                  {"scenario":"traveler wants to rebook a flight"}
                ]
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"New question?\",\"expected_output\":\"New answer\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("`--variation single-turn` requires single-turn goldens."));
    }

    @Test
    void generateRequiresMethodSpecificInput() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"generate", "--method", "contexts", "--variation", "single-turn"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--contexts-file"));
    }

    @Test
    void generateContextsRejectsNonListContextsFileLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "{\"context\":[\"Paris is in France.\"]}");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Contexts file must contain a JSON list of context lists."));
    }

    @Test
    void generateContextsRejectsNonStringContextChunksLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"], [42]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Contexts file must be shaped like [[\"chunk 1\", \"chunk 2\"], ...]."));
    }

    @Test
    void generateContextsReportsMissingContextsFileLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("missing-contexts.json");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Contexts file not found: " + contexts));
    }

    @Test
    void generateContextsReportsInvalidJsonContextsFileLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("invalid-contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"],");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Contexts file must be valid JSON:"));
    }

    @Test
    void generateDocsChunksDocumentAndWritesGoldensFile() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                {"data":[{"input":"Question two?","expected_output":"Answer two"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(), "--chunk-size", "2",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs"
        }, out, err);

        assertEquals(0, exit);
        var generated = Files.readString(output.resolve("docs.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("Question two?"));
        assertTrue(generated.contains("policy.md"));
    }

    @Test
    void generateDocsSupportsDeepEvalDocumentsAlias() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                {"data":[{"input":"Question two?","expected_output":"Answer two"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--documents", document.toString(), "--chunk-size", "2",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-alias"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-alias.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("policy.md"));
    }

    @Test
    void generateDocsHonorsMaxContextsPerDocumentLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta epsilon zeta");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                {"data":[{"input":"Question two?","expected_output":"Answer two"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(), "--chunk-size", "1",
                "--max-contexts-per-document", "2",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-limited"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-limited.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("Question two?"));
    }

    @Test
    void generateDocsUsesDeepEvalDefaultChunkSize() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, """
                one two three four five six seven eight nine ten
                eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty
                twentyone twentytwo twentythree twentyfour twentyfive twentysix twentyseven twentyeight twentynine thirty
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(),
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-default-chunk"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-default-chunk.json"));
        assertTrue(generated.contains("Question one?"));
    }

    @Test
    void generateDocsHonorsChunkOverlapLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta epsilon");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                {"data":[{"input":"Question two?","expected_output":"Answer two"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(), "--chunk-size", "3", "--chunk-overlap", "1",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-overlap"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-overlap.json"));
        assertTrue(generated.contains("alpha beta gamma"));
        assertTrue(generated.contains("gamma delta epsilon"));
    }

    @Test
    void generateConversationalDocsHonorsMaxContextsPerDocumentLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma delta epsilon zeta");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"scenario":"Scenario one","turns":[{"role":"user","content":"Question one?"}],"expected_outcome":"Outcome one"}]}
                {"data":[{"scenario":"Scenario two","turns":[{"role":"user","content":"Question two?"}],"expected_outcome":"Outcome two"}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "multi-turn",
                "--document-path", document.toString(), "--chunk-size", "1",
                "--max-contexts-per-document", "2", "--max-goldens-per-context", "1",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-limited-conv"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-limited-conv.json"));
        assertTrue(generated.contains("Scenario one"));
        assertTrue(generated.contains("Scenario two"));
    }

    @Test
    void generateDocsRequiresDocumentPath() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"generate", "--method", "docs", "--variation", "single-turn"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--document-path"));
    }

    @Test
    void generateMultiTurnContextsWritesConversationalGoldensFile() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Refunds are available within 30 days.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"scenario\":\"refund support\",\"turns\":[{\"role\":\"user\",\"content\":\"Need a refund\"},{\"role\":\"assistant\",\"content\":\"I can help\"}],\"expected_outcome\":\"Refund path explained\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "multi-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "conversations"
        }, out, err);

        assertEquals(0, exit);
        var generated = Files.readString(output.resolve("conversations.json"));
        assertTrue(generated.contains("\"scenario\" : \"refund support\""));
        assertTrue(generated.contains("\"turns\""));
        assertTrue(generated.contains("\"expected_outcome\" : \"Refund path explained\""));
    }

    @Test
    void generateMultiTurnScratchUsesConversationalStyling() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"scenario\":\"flight booking\",\"turns\":[{\"role\":\"user\",\"content\":\"Book a flight\"}],\"expected_outcome\":\"Flight booking started\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "multi-turn",
                "--scenario-context", "travel support", "--conversational-task", "book flights",
                "--participant-roles", "traveler and agent", "--num-goldens", "1",
                "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "scratch-conversations"
        }, out, err);

        assertEquals(0, exit);
        assertTrue(Files.readString(output.resolve("scratch-conversations.json")).contains("flight booking"));
    }

    @Test
    void generateMultiTurnGoldensWritesConversationalGoldensFile() throws Exception {
        var goldens = tempDir.resolve("goldens.json");
        Files.writeString(goldens, """
                [
                  {"scenario":"traveler wants to rebook a flight"}
                ]
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"scenario\":\"flight change request\",\"turns\":[{\"role\":\"user\",\"content\":\"Change my flight\"}],\"expected_outcome\":\"Flight change started\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "multi-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "multi-goldens"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(Files.readString(output.resolve("multi-goldens.json")).contains("flight change request"));
    }

    @Test
    void settingsSetUnsetListAndMaskSecretsInDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var setExit = run(new String[] {
                "settings", "-u", "log-level=error", "-u", "temperature=0.92",
                "-u", "anthropic-api-key=sk-test", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(0, setExit);
        assertDotenv(env, "LOG_LEVEL", "40");
        assertDotenv(env, "TEMPERATURE", "0.92");
        assertDotenv(env, "ANTHROPIC_API_KEY", "sk-test");

        out.reset();
        err.reset();
        var listExit = run(new String[] {"settings", "-l", "anthropic", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, listExit);
        assertTrue(text(out).contains("ANTHROPIC_API_KEY=********"));
        assertEquals(false, text(out).contains("sk-test"));

        out.reset();
        err.reset();
        var unsetExit = run(new String[] {"settings", "-U", "temperature", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, unsetExit);
        assertEquals(false, Files.readString(env).contains("TEMPERATURE="));
        assertEquals(1, countKey(env, "LOG_LEVEL"));
    }

    @Test
    void settingsListWithoutFilterPrintsAllSavedSettings() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "-u", "log-level=debug", "-u", "openai-api-key=sk-test",
                "--save", "dotenv:" + env
        }, out, err));
        out.reset();
        err.reset();

        var exit = run(new String[] {"settings", "--list", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("LOG_LEVEL=10"));
        assertTrue(text(out).contains("OPENAI_API_KEY=********"));
    }

    @Test
    void settingsAcceptsSaveEqualsDotenvForm() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "-u", "log-level=info", "--save=dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "LOG_LEVEL", "20");
    }

    @Test
    void setDebugQuietUpdatesDotenvWithoutOutput() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-debug", "--log-level", "DEBUG", "--save", "dotenv:" + env, "--quiet"},
                out, err);

        assertEquals(0, exit);
        assertEquals("", text(out));
        assertDotenv(env, "LOG_LEVEL", "10");
    }

    @Test
    void setDebugAcceptsShortSaveAndQuietAliases() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-debug", "--log-level", "INFO", "-s", "dotenv:" + env, "-q"},
                out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
        assertDotenv(env, "LOG_LEVEL", "20");
    }

    @Test
    void setDebugPersistsExplicitDebugOptions() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-debug",
                "--log-level", "WARNING",
                "--verbose", "true",
                "--debug-async", "true",
                "--log-stack-traces", "true",
                "--retry-before-level", "INFO",
                "--retry-after-level", "ERROR",
                "--grpc", "true",
                "--grpc-verbosity", "DEBUG",
                "--grpc-trace", "api",
                "--trace-verbose", "true",
                "--trace-env", "staging",
                "--trace-flush", "true",
                "--trace-sample-rate", "0.25",
                "--save", "dotenv:" + env,
                "--quiet"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
        assertDotenv(env, "LOG_LEVEL", "30");
        assertDotenv(env, "DEEPEVAL_VERBOSE_MODE", "true");
        assertDotenv(env, "DEEPEVAL_DEBUG_ASYNC", "true");
        assertDotenv(env, "DEEPEVAL_LOG_STACK_TRACES", "true");
        assertDotenv(env, "DEEPEVAL_RETRY_BEFORE_LOG_LEVEL", "20");
        assertDotenv(env, "DEEPEVAL_RETRY_AFTER_LOG_LEVEL", "40");
        assertDotenv(env, "DEEPEVAL_GRPC_LOGGING", "true");
        assertDotenv(env, "GRPC_VERBOSITY", "DEBUG");
        assertDotenv(env, "GRPC_TRACE", "api");
        assertDotenv(env, "CONFIDENT_TRACE_VERBOSE", "true");
        assertDotenv(env, "CONFIDENT_TRACE_ENVIRONMENT", "staging");
        assertDotenv(env, "CONFIDENT_TRACE_FLUSH", "true");
        assertDotenv(env, "CONFIDENT_TRACE_SAMPLE_RATE", "0.25");
    }

    @Test
    void unsetDebugRemovesDebugSettingsFromDotenv() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "-u", "log-level=debug", "-u", "deepeval-verbose-mode=true",
                "-u", "deepeval-debug-async=true", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "LOG_LEVEL", "10");
        assertDotenv(env, "DEEPEVAL_VERBOSE_MODE", "true");
        assertDotenv(env, "DEEPEVAL_DEBUG_ASYNC", "true");

        out.reset();
        err.reset();
        var exit = run(new String[] {"unset-debug", "--save", "dotenv:" + env, "--quiet"}, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
        assertEquals(false, readDotenv(env).containsKey("LOG_LEVEL"));
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_VERBOSE_MODE"));
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_DEBUG_ASYNC"));
    }

    @Test
    void providerAcceptsShortSaveAlias() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "-s", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "USE_OPENAI_MODEL", "YES");
        assertDotenv(env, "OPENAI_MODEL_NAME", "gpt-4o-mini");
    }

    @Test
    void providerSetUnsetRoundtripUsesExclusiveFlags() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_OPENAI_MODEL", "YES");
        assertDotenv(env, "OPENAI_MODEL_NAME", "gpt-4o-mini");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-ollama", "--model", "llama3", "--base-url", "http://localhost:11434/", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_LOCAL_MODEL", "YES");
        assertDotenv(env, "OLLAMA_MODEL_NAME", "llama3");
        assertDotenv(env, "LOCAL_MODEL_BASE_URL", "http://localhost:11434/");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-ollama", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_LOCAL_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OLLAMA_MODEL_NAME"));
    }

    @Test
    void openAiProviderPersistsTemperatureAndCostOverrides() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "custom-model",
                "--temperature", "0.1",
                "--cost-per-input-token", "0.0005",
                "--cost-per-output-token", "0.0015",
                "--save", "dotenv:" + env
        }, out, err));

        assertDotenv(env, "USE_OPENAI_MODEL", "YES");
        assertDotenv(env, "OPENAI_MODEL_NAME", "custom-model");
        assertDotenv(env, "TEMPERATURE", "0.1");
        assertDotenv(env, "OPENAI_COST_PER_INPUT_TOKEN", "0.0005");
        assertDotenv(env, "OPENAI_COST_PER_OUTPUT_TOKEN", "0.0015");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openai", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_COST_PER_INPUT_TOKEN"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_COST_PER_OUTPUT_TOKEN"));
        assertDotenv(env, "TEMPERATURE", "0.1");
    }

    @Test
    void unsetOpenAiClearSecretsRemovesApiKey() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "-u", "openai-api-key=sk-test", "--save", "dotenv:" + env
        }, out, err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openai", "--save", "dotenv:" + env}, out, err));
        assertDotenv(env, "OPENAI_API_KEY", "sk-test");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openai", "--clear-secrets", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_API_KEY"));
    }

    @Test
    void providerUnsetClearSecretsRemovesProviderSecrets() throws Exception {
        assertProviderSecretClearing(
                "anthropic-api-key",
                "ANTHROPIC_API_KEY",
                "set-anthropic",
                new String[] {"--model", "claude-3-5-sonnet"},
                "unset-anthropic");
        assertProviderSecretClearing(
                "openrouter-api-key",
                "OPENROUTER_API_KEY",
                "set-openrouter",
                new String[] {"--model", "openai/gpt-4.1"},
                "unset-openrouter");
        assertProviderSecretClearing(
                "google-api-key",
                "GOOGLE_API_KEY",
                "set-gemini",
                new String[] {"--model", "gemini-2.5-flash"},
                "unset-gemini");
        assertProviderSecretClearing(
                "aws-secret-access-key",
                "AWS_SECRET_ACCESS_KEY",
                "set-bedrock",
                new String[] {"--model", "anthropic.claude", "--region", "us-east-1"},
                "unset-bedrock");
    }

    @Test
    void azureProviderAcceptsDeepEvalShortAliasesAndModelVersion() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-azure-openai",
                "-m", "gpt-4.1",
                "-d", "prod-deployment",
                "-u", "https://azure.example/",
                "-v", "2024-02-15-preview",
                "-V", "0125",
                "-s", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "USE_AZURE_OPENAI", "YES");
        assertDotenv(env, "AZURE_MODEL_NAME", "gpt-4.1");
        assertDotenv(env, "AZURE_DEPLOYMENT_NAME", "prod-deployment");
        assertDotenv(env, "AZURE_OPENAI_ENDPOINT", "https://azure.example/");
        assertDotenv(env, "OPENAI_API_VERSION", "2024-02-15-preview");
        assertDotenv(env, "AZURE_MODEL_VERSION", "0125");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-azure-openai", "-s", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_AZURE_OPENAI"));
        assertEquals(false, readDotenv(env).containsKey("AZURE_MODEL_VERSION"));
    }

    @Test
    void localModelProviderAcceptsDeepEvalShortAliases() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-local-model",
                "-m", "local-eval",
                "-u", "http://localhost:8000/v1",
                "-f", "json",
                "-s", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "USE_LOCAL_MODEL", "YES");
        assertDotenv(env, "LOCAL_MODEL_NAME", "local-eval");
        assertDotenv(env, "LOCAL_MODEL_BASE_URL", "http://localhost:8000/v1");
        assertDotenv(env, "LOCAL_MODEL_FORMAT", "json");
    }

    @Test
    void openRouterProviderRoundtripUsesExclusiveFlags() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-openrouter", "--model", "openai/gpt-4.1",
                "--base-url", "https://openrouter.ai/api/v1", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_OPENROUTER_MODEL", "YES");
        assertDotenv(env, "OPENROUTER_MODEL_NAME", "openai/gpt-4.1");
        assertDotenv(env, "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openrouter", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_OPENROUTER_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_BASE_URL"));
    }

    @Test
    void openRouterProviderPersistsTemperatureAndCostOverrides() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openrouter", "--model", "openai/gpt-4.1",
                "--base-url", "https://openrouter.ai/api/v1",
                "--temperature", "0.3",
                "--cost-per-input-token", "0.0007",
                "--cost-per-output-token", "0.0021",
                "--save", "dotenv:" + env
        }, out, err));

        assertDotenv(env, "USE_OPENROUTER_MODEL", "YES");
        assertDotenv(env, "OPENROUTER_MODEL_NAME", "openai/gpt-4.1");
        assertDotenv(env, "OPENROUTER_BASE_URL", "https://openrouter.ai/api/v1");
        assertDotenv(env, "TEMPERATURE", "0.3");
        assertDotenv(env, "OPENROUTER_COST_PER_INPUT_TOKEN", "0.0007");
        assertDotenv(env, "OPENROUTER_COST_PER_OUTPUT_TOKEN", "0.0021");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openrouter", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_OPENROUTER_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_BASE_URL"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_COST_PER_INPUT_TOKEN"));
        assertEquals(false, readDotenv(env).containsKey("OPENROUTER_COST_PER_OUTPUT_TOKEN"));
        assertDotenv(env, "TEMPERATURE", "0.3");
    }

    @Test
    void geminiProviderSetsVertexFlagForProjectOrLocation() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-gemini", "--model", "gemini-2.5-flash",
                "--project", "jeval-project", "--location", "us-central1",
                "--save", "dotenv:" + env
        }, out, err));

        assertDotenv(env, "USE_GEMINI_MODEL", "YES");
        assertDotenv(env, "GEMINI_MODEL_NAME", "gemini-2.5-flash");
        assertDotenv(env, "GOOGLE_CLOUD_PROJECT", "jeval-project");
        assertDotenv(env, "GOOGLE_CLOUD_LOCATION", "us-central1");
        assertDotenv(env, "GOOGLE_GENAI_USE_VERTEXAI", "true");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-gemini", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_GEMINI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("GEMINI_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("GOOGLE_CLOUD_PROJECT"));
        assertEquals(false, readDotenv(env).containsKey("GOOGLE_CLOUD_LOCATION"));
        assertEquals(false, readDotenv(env).containsKey("GOOGLE_GENAI_USE_VERTEXAI"));
    }

    @Test
    void geminiProviderLoadsServiceAccountFileAndSetsVertexFlag() throws Exception {
        var env = tempDir.resolve(".env");
        var serviceAccount = tempDir.resolve("service-account.json");
        Files.writeString(serviceAccount, "{\"type\":\"service_account\",\"project_id\":\"jeval\"}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-gemini", "--model", "gemini-2.5-flash",
                "--service-account-file", serviceAccount.toString(),
                "--save", "dotenv:" + env
        }, out, err));

        assertDotenv(env, "USE_GEMINI_MODEL", "YES");
        assertDotenv(env, "GEMINI_MODEL_NAME", "gemini-2.5-flash");
        assertDotenv(env, "GOOGLE_SERVICE_ACCOUNT_KEY", "{\"type\":\"service_account\",\"project_id\":\"jeval\"}");
        assertDotenv(env, "GOOGLE_GENAI_USE_VERTEXAI", "true");
    }

    private static PrintStream print(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private int run(String[] args, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return JEvalCli.run(args, print(out), print(err), tempDir);
    }

    private void assertProviderSecretClearing(
            String settingName,
            String envKey,
            String setCommand,
            String[] setArgs,
            String unsetCommand) throws Exception {
        var env = tempDir.resolve(settingName + ".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "-u", settingName + "=secret-value", "--save", "dotenv:" + env
        }, out, err));
        out.reset();
        err.reset();
        assertEquals(0, run(providerArgs(setCommand, setArgs, env), out, err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {unsetCommand, "--save", "dotenv:" + env}, out, err));
        assertDotenv(env, envKey, "secret-value");

        out.reset();
        err.reset();
        assertEquals(0, run(providerArgs(setCommand, setArgs, env), out, err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {unsetCommand, "--clear-secrets", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey(envKey));
    }

    private static String[] providerArgs(String command, String[] setArgs, Path env) {
        var args = new java.util.ArrayList<String>();
        args.add(command);
        args.addAll(java.util.List.of(setArgs));
        args.add("--save");
        args.add("dotenv:" + env);
        return args.toArray(String[]::new);
    }

    private static void withDefaultDotenv(String content, CheckedRunnable action) throws Exception {
        var path = Path.of(".env");
        var existed = Files.exists(path);
        var original = existed ? Files.readString(path) : null;
        Files.writeString(path, content);
        try {
            action.run();
        } finally {
            if (existed) {
                Files.writeString(path, original);
            } else {
                Files.deleteIfExists(path);
            }
        }
    }

    private static String text(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static void assertDotenv(Path path, String key, String value) throws Exception {
        assertEquals(value, readDotenv(path).get(key));
    }

    private static int countKey(Path path, String key) throws Exception {
        if (!Files.exists(path)) {
            return 0;
        }
        var prefix = key + "=";
        return (int) Files.readAllLines(path).stream().filter(line -> line.startsWith(prefix)).count();
    }

    private static java.util.Map<String, String> readDotenv(Path path) throws Exception {
        var values = new java.util.LinkedHashMap<String, String>();
        if (!Files.exists(path)) {
            return values;
        }
        for (var line : Files.readAllLines(path)) {
            var index = line.indexOf('=');
            if (index > 0) {
                values.put(line.substring(0, index), line.substring(index + 1));
            }
        }
        return values;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
