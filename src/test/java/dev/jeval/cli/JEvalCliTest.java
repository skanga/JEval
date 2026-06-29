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

    @Test
    void generateCommandRequiresResponsesFileOrProviderSettings() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "users", "--task", "answer", "--input-format", "question"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No supported provider"));
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
    void generateRequiresMethodSpecificInput() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"generate", "--method", "contexts", "--variation", "single-turn"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--contexts-file"));
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
                "--participant-roles", "traveler and agent", "--responses-file", responses.toString(),
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

    private static PrintStream print(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }

    private int run(String[] args, ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return JEvalCli.run(args, print(out), print(err), tempDir);
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
}
