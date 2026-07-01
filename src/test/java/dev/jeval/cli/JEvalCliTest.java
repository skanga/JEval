package dev.jeval.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
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
    void usageListsProviderBackedGenerationCommands() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains(
                "set-openai|set-azure-openai|set-bedrock|set-anthropic|set-gemini|set-grok|set-moonshot|set-deepseek|set-litellm|set-portkey|set-ollama|set-local-model|set-openrouter"));
    }

    @Test
    void usageListsEmbeddingProviderCommands() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains(
                "set-azure-openai-embedding|set-local-embeddings|set-ollama-embeddings"));
    }

    @Test
    void versionOptionsPrintVersionAndExitZero() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"--version"}, out, err));
        assertTrue(text(out).contains("0.1.0-SNAPSHOT"));
        assertEquals("", text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"-V"}, out, err));
        assertTrue(text(out).contains("0.1.0-SNAPSHOT"));
        assertEquals("", text(err));
    }

    @Test
    void helpOptionsPrintUsageAndExitZero() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"--help"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"-h"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));
    }

    @Test
    void commandHelpOptionsPrintUsageAndExitZero() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"test", "--help"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"generate", "-h"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));
    }

    @Test
    void testRunHelpOptionsPrintUsageAndExitZero() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"test", "run", "--help"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"test", "run", "-h"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));
    }

    @Test
    void providerCommandHelpOptionsPrintUsageAndExitZero() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {"set-openai", "--help"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openai", "-h"}, out, err));
        assertTrue(text(out).contains("Usage: jeval"));
        assertEquals("", text(err));
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
    void testRunAcceptsEqualsFormForDeepEvalOptions() throws Exception {
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
                "test", "run", file.toString(),
                "--identifier=equals-smoke", "--format=html", "--output=" + output
        }, out, err);

        assertEquals(0, exit, text(err));
        var report = output.resolve("equals-smoke.html");
        assertTrue(Files.exists(report));
        assertTrue(Files.readString(report).contains("equals-smoke"));
    }

    @Test
    void testRunAcceptsShortEqualsFormForDeepEvalOptions() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "spec-name",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "smoke-good", "tags": ["smoke"], "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "regression-bad", "tags": ["regression"], "input": "q", "actualOutput": "a", "expectedOutput": "b"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "test", "run", file.toString(),
                "-id=short-equals", "-r=2", "-d=passing", "-m=smoke"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("short-equals"));
        assertTrue(text(out).contains("Summary: total=2 passed=2 failed=0"));
        assertEquals(false, text(out).contains("regression-bad"));
    }

    @Test
    void testRunWritesDeepEvalLatestTestRunData() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "spec-name",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "tags": ["smoke"], "input": "q", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "bad", "tags": ["regression"], "input": "q", "actualOutput": "a", "expectedOutput": "b",
                     "context": ["document context"], "retrieval_context": ["retrieved fact"],
                     "metadata": {"suite": "nightly", "priority": 2}, "comments": "investigate drift",
                     "token_cost": 0.12, "completion_time": 1.5,
                     "custom_column_key_values": {"risk": "high", "case_id": "case-17"},
                     "tools_called": [{"name": "PolicySearch", "input_parameters": {"query": "refund"}, "output": "30 days"}],
                     "expected_tools": [{"name": "PolicySearch"}],
                     "mcp_servers": [{"server_name": "policy"}],
                     "mcp_tools_called": [{"name": "mcp-search"}],
                     "mcp_resources_called": [{"uri": "file://policy"}],
                     "mcp_prompts_called": [{"name": "policy-prompt"}],
                     "trace": {"name": "root", "spans": [{"name": "retriever", "score": 0.8}]}}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--identifier", "release-smoke", "--quiet"}, out, err);

        assertEquals(1, exit, text(err));
        var latest = tempDir.resolve(".deepeval").resolve(".latest_test_run.json");
        var latestText = Files.readString(latest);
        assertTrue(latestText.contains("\"testRunData\""));
        assertTrue(latestText.contains("\"identifier\":\"release-smoke\""));
        assertTrue(latestText.contains("\"testPassed\":1"));
        assertTrue(latestText.contains("\"testFailed\":1"));
        assertTrue(latestText.contains("\"metric\":\"Exact Match\""));
        assertTrue(latestText.contains("\"input\":\"q\""));
        assertTrue(latestText.contains("\"actualOutput\":\"a\""));
        assertTrue(latestText.contains("\"expectedOutput\":\"b\""));
        assertTrue(latestText.contains("\"context\":[\"document context\"]"));
        assertTrue(latestText.contains("\"retrievalContext\":[\"retrieved fact\"]"));
        assertTrue(latestText.contains("\"tags\":[\"regression\"]"));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"nightly\""));
        assertTrue(latestText.contains("\"priority\":2"));
        assertTrue(latestText.contains("\"comments\":\"investigate drift\""));
        assertTrue(latestText.contains("\"tokenCost\":0.12"));
        assertTrue(latestText.contains("\"completionTime\":1.5"));
        assertTrue(latestText.contains("\"customColumnKeyValues\":{"));
        assertTrue(latestText.contains("\"risk\":\"high\""));
        assertTrue(latestText.contains("\"case_id\":\"case-17\""));
        assertTrue(latestText.contains("\"toolsCalled\":[{"));
        assertTrue(latestText.contains("\"expectedTools\":[{"));
        assertTrue(latestText.contains("\"name\":\"PolicySearch\""));
        assertTrue(latestText.contains("\"inputParameters\":{\"query\":\"refund\"}"));
        assertTrue(latestText.contains("\"output\":\"30 days\""));
        assertTrue(latestText.contains("\"mcpServers\":[{\"server_name\":\"policy\"}]"));
        assertTrue(latestText.contains("\"mcpToolsCalled\":[{\"name\":\"mcp-search\"}]"));
        assertTrue(latestText.contains("\"mcpResourcesCalled\":[{\"uri\":\"file://policy\"}]"));
        assertTrue(latestText.contains("\"mcpPromptsCalled\":[{\"name\":\"policy-prompt\"}]"));
        assertTrue(latestText.contains("\"trace\":{\"name\":\"root\""));
        assertTrue(latestText.contains("\"spans\":[{\"name\":\"retriever\",\"score\":0.8}]"));
    }

    @Test
    void testRunDatasetPreservesDeepEvalToolAndMcpFieldsInLatestData() throws Exception {
        var dataset = tempDir.resolve("cases.json");
        Files.writeString(dataset, """
                [
                  {
                    "name": "dataset-bad",
                    "tags": ["json"],
                    "input": "q",
                    "actual_output": "a",
                    "expected_output": "b",
                    "context": ["document context"],
                    "retrieval_context": ["retrieved fact"],
                    "tools_called": [{"name": "PolicySearch", "input_parameters": {"query": "refund"}, "output": "30 days"}],
                    "expected_tools": [{"name": "PolicySearch"}],
                    "mcp_servers": [{"server_name": "policy"}],
                    "mcp_tools_called": [{"name": "mcp-search"}],
                    "mcp_resources_called": [{"uri": "file://policy"}],
                    "mcp_prompts_called": [{"name": "policy-prompt"}],
                    "metadata": {"suite": "dataset"},
                    "comments": "json comment",
                    "token_cost": 0.32,
                    "completion_time": 3.5,
                    "custom_column_key_values": {"risk": "medium"},
                    "trace": {"name": "root", "spans": [{"name": "retriever", "score": 0.8}]}
                  }
                ]
                """);
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "dataset-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "cases.json"
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--identifier", "dataset-release", "--quiet"},
                out, err);

        assertEquals(1, exit, text(err));
        var latestText = Files.readString(tempDir.resolve(".deepeval").resolve(".latest_test_run.json"));
        assertTrue(latestText.contains("\"identifier\":\"dataset-release\""));
        assertTrue(latestText.contains("\"name\":\"dataset-bad\""));
        assertTrue(latestText.contains("\"context\":[\"document context\"]"));
        assertTrue(latestText.contains("\"retrievalContext\":[\"retrieved fact\"]"));
        assertTrue(latestText.contains("\"tags\":[\"json\"]"));
        assertTrue(latestText.contains("\"toolsCalled\":[{"));
        assertTrue(latestText.contains("\"expectedTools\":[{"));
        assertTrue(latestText.contains("\"name\":\"PolicySearch\""));
        assertTrue(latestText.contains("\"inputParameters\":{\"query\":\"refund\"}"));
        assertTrue(latestText.contains("\"output\":\"30 days\""));
        assertTrue(latestText.contains("\"mcpServers\":[{\"server_name\":\"policy\"}]"));
        assertTrue(latestText.contains("\"mcpToolsCalled\":[{\"name\":\"mcp-search\"}]"));
        assertTrue(latestText.contains("\"mcpResourcesCalled\":[{\"uri\":\"file://policy\"}]"));
        assertTrue(latestText.contains("\"mcpPromptsCalled\":[{\"name\":\"policy-prompt\"}]"));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"dataset\""));
        assertTrue(latestText.contains("\"comments\":\"json comment\""));
        assertTrue(latestText.contains("\"tokenCost\":0.32"));
        assertTrue(latestText.contains("\"completionTime\":3.5"));
        assertTrue(latestText.contains("\"customColumnKeyValues\":{\"risk\":\"medium\"}"));
        assertTrue(latestText.contains("\"trace\":{\"name\":\"root\""));
    }

    @Test
    void testRunJsonlDatasetPreservesDeepEvalLatestData() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"name":"jsonl-bad","tags":["jsonl"],"input":"q","actual_output":"a","expected_output":"b","context":["document context"],"retrieval_context":["retrieved fact"],"metadata":{"suite":"jsonl"},"comments":"jsonl comment","token_cost":0.42,"completion_time":2.5,"custom_column_key_values":{"risk":"medium"},"tools_called":[{"name":"PolicySearch","input_parameters":{"query":"refund"},"output":"30 days"}],"expected_tools":[{"name":"PolicySearch"}],"mcp_servers":[{"server_name":"policy"}],"mcp_tools_called":[{"name":"mcp-search"}],"mcp_resources_called":[{"uri":"file://policy"}],"mcp_prompts_called":[{"name":"policy-prompt"}],"trace":{"name":"root","spans":[{"name":"retriever","score":0.8}]}}
                """);
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "jsonl-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "cases.jsonl"
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--identifier", "jsonl-release", "--quiet"},
                out, err);

        assertEquals(1, exit, text(err));
        var latestText = Files.readString(tempDir.resolve(".deepeval").resolve(".latest_test_run.json"));
        assertTrue(latestText.contains("\"identifier\":\"jsonl-release\""));
        assertTrue(latestText.contains("\"name\":\"jsonl-bad\""));
        assertTrue(latestText.contains("\"context\":[\"document context\"]"));
        assertTrue(latestText.contains("\"retrievalContext\":[\"retrieved fact\"]"));
        assertTrue(latestText.contains("\"tags\":[\"jsonl\"]"));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"jsonl\""));
        assertTrue(latestText.contains("\"comments\":\"jsonl comment\""));
        assertTrue(latestText.contains("\"tokenCost\":0.42"));
        assertTrue(latestText.contains("\"completionTime\":2.5"));
        assertTrue(latestText.contains("\"customColumnKeyValues\":{\"risk\":\"medium\"}"));
        assertTrue(latestText.contains("\"toolsCalled\":[{"));
        assertTrue(latestText.contains("\"expectedTools\":[{"));
        assertTrue(latestText.contains("\"inputParameters\":{\"query\":\"refund\"}"));
        assertTrue(latestText.contains("\"output\":\"30 days\""));
        assertTrue(latestText.contains("\"mcpServers\":[{\"server_name\":\"policy\"}]"));
        assertTrue(latestText.contains("\"mcpToolsCalled\":[{\"name\":\"mcp-search\"}]"));
        assertTrue(latestText.contains("\"mcpResourcesCalled\":[{\"uri\":\"file://policy\"}]"));
        assertTrue(latestText.contains("\"mcpPromptsCalled\":[{\"name\":\"policy-prompt\"}]"));
        assertTrue(latestText.contains("\"trace\":{\"name\":\"root\""));
    }

    @Test
    void testRunJsonlDatasetRejectsMissingRequiredFieldsLikeDeepEval() throws Exception {
        var dataset = tempDir.resolve("missing-required.jsonl");
        Files.writeString(dataset, """
                {"input":"q","expected_output":"b"}
                """);
        var file = tempDir.resolve("missing-required-eval.json");
        Files.writeString(file, """
                {
                  "name": "missing-required-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "missing-required.jsonl"
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--quiet"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Required fields are missing in one or more JSON objects"));
    }

    @Test
    void testRunCsvDatasetPreservesDeepEvalToolAndMetadataFieldsInLatestData() throws Exception {
        var dataset = tempDir.resolve("cases.csv");
        Files.writeString(dataset, """
                name,tags,input,actual_output,expected_output,tools_called,expected_tools,metadata,comments,token_cost,completion_time,custom_column_key_values,mcp_servers,mcp_tools_called,mcp_resources_called,mcp_prompts_called,trace
                csv-bad,smoke;csv,q,a,b,"[{""name"":""PolicySearch"",""input_parameters"":{""query"":""refund""},""output"":""30 days""}]","[{""name"":""PolicySearch""}]","{""suite"":""csv""}",csv comment,0.52,4.5,"{""risk"":""medium""}","[{""server_name"":""policy""}]","[{""name"":""mcp-search""}]","[{""uri"":""file://policy""}]","[{""name"":""policy-prompt""}]","{""name"":""root"",""spans"":[{""name"":""retriever"",""score"":0.8}]}"
                """);
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "csv-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "cases.csv"
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--identifier", "csv-release", "--quiet"},
                out, err);

        assertEquals(1, exit, text(err));
        var latestText = Files.readString(tempDir.resolve(".deepeval").resolve(".latest_test_run.json"));
        assertTrue(latestText.contains("\"identifier\":\"csv-release\""));
        assertTrue(latestText.contains("\"name\":\"csv-bad\""));
        assertTrue(latestText.contains("\"tags\":[\"smoke\",\"csv\"]"));
        assertTrue(latestText.contains("\"toolsCalled\":[{"));
        assertTrue(latestText.contains("\"expectedTools\":[{"));
        assertTrue(latestText.contains("\"name\":\"PolicySearch\""));
        assertTrue(latestText.contains("\"inputParameters\":{\"query\":\"refund\"}"));
        assertTrue(latestText.contains("\"output\":\"30 days\""));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"csv\""));
        assertTrue(latestText.contains("\"comments\":\"csv comment\""));
        assertTrue(latestText.contains("\"tokenCost\":0.52"));
        assertTrue(latestText.contains("\"completionTime\":4.5"));
        assertTrue(latestText.contains("\"customColumnKeyValues\":{\"risk\":\"medium\"}"));
        assertTrue(latestText.contains("\"mcpServers\":[{\"server_name\":\"policy\"}]"));
        assertTrue(latestText.contains("\"mcpToolsCalled\":[{\"name\":\"mcp-search\"}]"));
        assertTrue(latestText.contains("\"mcpResourcesCalled\":[{\"uri\":\"file://policy\"}]"));
        assertTrue(latestText.contains("\"mcpPromptsCalled\":[{\"name\":\"policy-prompt\"}]"));
        assertTrue(latestText.contains("\"trace\":{\"name\":\"root\""));
    }

    @Test
    void testRunDatasetsAcceptDeepEvalAdditionalMetadataAlias() throws Exception {
        var jsonDataset = tempDir.resolve("alias-cases.json");
        Files.writeString(jsonDataset, """
                [
                  {"input": "q", "actual_output": "a", "expected_output": "b",
                   "additional_metadata": {"suite": "json-alias"}}
                ]
                """);
        var jsonSpec = tempDir.resolve("alias-json-eval.json");
        Files.writeString(jsonSpec, """
                {
                  "name": "json-alias-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "alias-cases.json"
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", jsonSpec.toString(), "--identifier", "json-alias", "--quiet"},
                out, err);

        assertEquals(1, exit, text(err));
        var latestText = Files.readString(tempDir.resolve(".deepeval").resolve(".latest_test_run.json"));
        assertTrue(latestText.contains("\"identifier\":\"json-alias\""));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"json-alias\""));

        var csvDataset = tempDir.resolve("alias-cases.csv");
        Files.writeString(csvDataset, """
                input,actual_output,expected_output,additional_metadata
                q,a,b,"{""suite"":""csv-alias""}"
                """);
        var csvSpec = tempDir.resolve("alias-csv-eval.json");
        Files.writeString(csvSpec, """
                {
                  "name": "csv-alias-spec",
                  "metrics": [{"type": "exact_match"}],
                  "dataset": "alias-cases.csv"
                }
                """);
        out.reset();
        err.reset();

        exit = run(new String[] {"test", "run", csvSpec.toString(), "--identifier", "csv-alias", "--quiet"},
                out, err);

        assertEquals(1, exit, text(err));
        latestText = Files.readString(tempDir.resolve(".deepeval").resolve(".latest_test_run.json"));
        assertTrue(latestText.contains("\"identifier\":\"csv-alias\""));
        assertTrue(latestText.contains("\"metadata\":{"));
        assertTrue(latestText.contains("\"suite\":\"csv-alias\""));
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
    void testRunRepeatAliasAcceptsNegativeValueForValidation() throws Exception {
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

        var exit = run(new String[] {"test", "run", file.toString(), "-r", "-1"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("repeat argument must be at least 1"));
    }

    @Test
    void testRunRepeatRejectsNonnumericValueWithoutThrowing() throws Exception {
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

        var exit = assertDoesNotThrow(() -> run(new String[] {"test", "run", file.toString(), "--repeat", "abc"},
                out, err));

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for --repeat"));
    }

    @Test
    void testRunRejectsMissingRepeatValueBeforeConsumingNextOption() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "repeat-missing-value",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"test", "run", file.toString(), "--repeat", "--quiet"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --repeat"));
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
    void testRunAcceptsUnknownPytestOptionsLikeDeepEval() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "pytest-extra-spec",
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
                "--tb", "short",
                "--maxfail=1",
                "--quiet"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("", text(out));
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
    void inspectAcceptsEqualsFormOptionsLikeOtherCliCommands() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "equals-inspect",
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
        var exit = assertDoesNotThrow(() -> run(new String[] {
                    "inspect",
                    "--folder=" + tempDir.resolve(".deepeval"),
                    "--format=html"
            }, out, err));

        assertEquals(0, exit, text(err));
        assertTrue(text(out).startsWith("<!doctype html>"));
        assertTrue(text(out).contains("equals-inspect"));
    }

    @Test
    void inspectFallsBackToExperimentsFolderLikeDeepEval() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "experiments-inspect",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        assertEquals(0, run(new String[] {
                "test", "run", file.toString(), "--quiet",
                "--results-folder", tempDir.resolve("experiments").toString()
        }, out, err));
        Files.delete(tempDir.resolve(".deepeval").resolve(".latest_run_full.json"));

        out.reset();
        err.reset();
        var exit = run(new String[] {"inspect"}, out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("experiments-inspect"));
    }

    @Test
    void inspectUsesDeepEvalResultsFolderEnvironmentFallback() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "env-folder-inspect",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var envFolder = tempDir.resolve("env-results");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        assertEquals(0, run(new String[] {
                "test", "run", file.toString(), "--quiet",
                "--results-folder", envFolder.toString()
        }, out, err));
        Files.delete(tempDir.resolve(".deepeval").resolve(".latest_run_full.json"));

        out.reset();
        err.reset();
        var exit = JEvalCli.run(
                new String[] {"inspect"},
                print(out),
                print(err),
                tempDir,
                Map.of("DEEPEVAL_RESULTS_FOLDER", envFolder.toString()));

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("env-folder-inspect"));
    }

    @Test
    void inspectRejectsUnknownOptionsLikeDeepEvalTyper() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"inspect", "--missing-option"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --missing-option"));
    }

    @Test
    void inspectRejectsMissingFolderValueBeforeConsumingNextOption() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"inspect", "--folder", "--format", "html"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --folder"));
    }

    @Test
    void testRunWritesDeepEvalResultsFolderAndSubfolder() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "folder-store",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "good", "input": "q", "actualOutput": "a", "expectedOutput": "a"}
                  ]
                }
                """);
        var target = tempDir.resolve("evals");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "test", "run", file.toString(), "--quiet",
                "--results-folder", target.toString(),
                "--results-subfolder", "prompt-v3"
        }, out, err);

        assertEquals(0, exit, text(err));
        var nested = target.resolve("prompt-v3");
        try (var files = Files.list(nested)) {
            var runs = files
                    .filter(path -> path.getFileName().toString().matches("test_run_\\d{8}_\\d{6}(?:_\\d+)?\\.json"))
                    .toList();
            assertEquals(1, runs.size());
            assertTrue(Files.readString(runs.getFirst()).contains("\"name\" : \"folder-store\""));
        }
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
    void generateSaveEqualsDotenvReadsDefaultDotenvLocalLikeDeepEval() throws Exception {
        withDefaultDotenvLocal("USE_OPENAI_MODEL=YES\nOPENAI_MODEL_NAME=gpt-4o-mini\n", () -> {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            var exit = run(new String[] {"generate", "--method", "scratch", "--variation", "single-turn",
                    "--scenario", "users", "--task", "answer", "--input-format", "question",
                    "--num-goldens", "1", "--save=dotenv"}, out, err);

            assertEquals(2, exit);
            assertTrue(text(err).contains("OPENAI_API_KEY is required"));
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
    void generatePassesModelOptionToProviderFactoryLikeDeepEval() throws Exception {
        var env = tempDir.resolve(".env");
        Files.writeString(env, """
                USE_OPENROUTER_MODEL=YES
                OPENROUTER_API_KEY=sk-or-test
                OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
                """);
        var method = GenerateCommand.class.getDeclaredMethod("synthesizer", String[].class);
        method.setAccessible(true);

        var synthesizer = method.invoke(null, (Object) new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", tempDir.resolve("contexts.json").toString(),
                "--model", "openai/gpt-4.1",
                "--save", "dotenv:" + env
        });

        assertTrue(synthesizer instanceof dev.jeval.synthesizer.Synthesizer);
    }

    @Test
    void generatePassesAsyncConcurrencyAndCostOptionsToSynthesizerLikeDeepEval() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[]}");
        var method = GenerateCommand.class.getDeclaredMethod("synthesizer", String[].class);
        method.setAccessible(true);

        var synthesizer = (dev.jeval.synthesizer.Synthesizer) method.invoke(null, (Object) new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--responses-file", responses.toString(),
                "--sync-mode", "--max-concurrent", "7", "--cost-tracking"
        });

        assertEquals(false, synthesizer.options().asyncMode());
        assertEquals(7, synthesizer.options().maxConcurrent());
        assertEquals(true, synthesizer.options().costTracking());
    }

    @Test
    void generateHonorsEvolutionOptionsLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(),
                "--responses-file", responses.toString(),
                "--num-evolutions", "2",
                "--evolutions", "comparative,constrained",
                "--no-include-expected",
                "--output-dir", output.toString(), "--file-name", "evolved"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("evolved.json"));
        assertTrue(generated.contains("\"Comparative\""));
        assertTrue(generated.contains("\"Constrained\""));
        assertFalse(generated.contains("\"Reasoning\""));
    }

    @Test
    void generateHonorsFiltrationOptionsLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Too vague"}]}
                {"feedback":"Needs a specific answerable question.","score":0.9}
                {"rewritten_input":"Which city is the capital of France?"}
                {"feedback":"Clear and answerable.","score":0.99}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(),
                "--responses-file", responses.toString(),
                "--synthetic-input-quality-threshold", "0.95",
                "--max-quality-retries", "2",
                "--no-include-expected",
                "--output-dir", output.toString(), "--file-name", "filtered"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("filtered.json"));
        assertTrue(generated.contains("Which city is the capital of France?"));
        assertTrue(generated.contains("\"synthetic_input_quality\" : 0.99"));
    }

    @Test
    void generateParsesDocsContextConstructionOptionsLikeDeepEval() throws Exception {
        var method = GenerateCommand.class.getDeclaredMethod("contextConstructionConfig", String[].class);
        method.setAccessible(true);

        var config = method.invoke(null, (Object) new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--max-contexts-per-document", "5",
                "--min-contexts-per-document", "2",
                "--max-context-length", "4",
                "--min-context-length", "2",
                "--chunk-size", "256",
                "--chunk-overlap", "32",
                "--context-quality-threshold", "0.72",
                "--context-similarity-threshold", "0.18",
                "--max-retries", "4",
                "--encoding", "UTF-16"
        });

        assertEquals(5, accessor(config, "maxContextsPerDocument"));
        assertEquals(2, accessor(config, "minContextsPerDocument"));
        assertEquals(4, accessor(config, "maxContextLength"));
        assertEquals(2, accessor(config, "minContextLength"));
        assertEquals(256, accessor(config, "chunkSize"));
        assertEquals(32, accessor(config, "chunkOverlap"));
        assertEquals(0.72, (Double) accessor(config, "contextQualityThreshold"), 0.0001);
        assertEquals(0.18, (Double) accessor(config, "contextSimilarityThreshold"), 0.0001);
        assertEquals(4, accessor(config, "maxRetries"));
        assertEquals("UTF-16", accessor(config, "encoding"));
    }

    @Test
    void generateRejectsInvalidDocsContextThresholdsLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--documents", document.toString(),
                "--context-similarity-threshold", "1.5"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("context_similarity_threshold must be between 0 and 1."));
        assertFalse(text(err).contains("No supported provider"));

        out.reset();
        err.reset();
        exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--documents", document.toString(),
                "--context-quality-threshold", "-0.1"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("context_quality_threshold must be between 0 and 1."));
        assertFalse(text(err).contains("No supported provider"));
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
        assertTrue(generated.contains("\"expected_output\" : \"Generated expected output\""));
    }

    @Test
    void generateTextToSqlWritesGoldensFile() throws Exception {
        var contexts = tempDir.resolve("schemas.json");
        Files.writeString(contexts, "[[\"CREATE TABLE users (id INT, active BOOLEAN);\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"How many users are active?"}]}
                {"sql":"SELECT COUNT(*) FROM users WHERE active = true;"}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "text-to-sql", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "sql-goldens"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("sql-goldens.json"));
        assertTrue(generated.contains("\"input\" : \"How many users are active?\""));
        assertTrue(generated.contains("\"expected_output\" : \"SELECT COUNT(*) FROM users WHERE active = true;\""));
        assertTrue(generated.contains("CREATE TABLE users"));
    }

    @Test
    void generateTextToSqlRejectsMultiTurnBeforeProviderLookup() throws Exception {
        var contexts = tempDir.resolve("schemas.json");
        Files.writeString(contexts, "[[\"CREATE TABLE users (id INT, active BOOLEAN);\"]]");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "text-to-sql", "--variation", "multi-turn",
                "--contexts-file", contexts.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--method text-to-sql only supports --variation single-turn"));
        assertFalse(text(err).contains("No supported provider"));
    }

    @Test
    void generatePrintsDeepEvalStyleSaveMessage() throws Exception {
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
                "--output-dir", output.toString(), "--file-name", "message"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertEquals("Synthetic goldens saved at " + output.resolve("message.json") + "!", text(out).trim());
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
    void generateAcceptsEqualsFormOptionsLikeDeepEvalTyper() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method=contexts", "--variation=single-turn",
                "--contexts-file=" + contexts,
                "--responses-file=" + responses,
                "--output-dir=" + output,
                "--file-name=equals-form"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("equals-form.json"));
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
            var generatedPath = generatedPathFromMessage(text(out));
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
    void generateSupportsDeepEvalIncludeExpectedAliasAfterNegativeFlag() throws Exception {
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
                "--no-include-expected", "--include-expected",
                "--output-dir", output.toString(), "--file-name", "with-expected"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("with-expected.json"));
        assertTrue(generated.contains("Capital?"));
        assertTrue(generated.contains("\"expected_output\" : \"Generated expected output\""));
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
    void generateScratchRejectsNonnumericNumGoldensWithoutRawJavaError() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--task", "study", "--input-format", "question",
                "--num-goldens", "abc", "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for --num-goldens: abc"));
    }

    @Test
    void generateRejectsMissingValueBeforeUnknownOptionLikeToken() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--task", "study", "--input-format", "question",
                "--num-goldens", "--bogus", "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --num-goldens"));
    }

    @Test
    void generateRejectsMissingValueBeforeUnknownShortOptionLikeToken() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Study question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "scratch", "--variation", "single-turn",
                "--scenario", "students", "--task", "study", "--input-format", "question",
                "--num-goldens", "-x", "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --num-goldens"));
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
        assertTrue(generated.contains("\"expected_output\" : null"));
    }

    @Test
    void generateGoldensAcceptsCsvSourceFile() throws Exception {
        var goldens = tempDir.resolve("goldens.csv");
        Files.writeString(goldens, """
                input,expected_output
                Old question?,Old answer
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, String.join(System.lineSeparator(),
                "{\"scenario\":\"students\",\"task\":\"ask study questions\",\"input_format\":\"one question\"}",
                "{\"data\":[{\"input\":\"Csv question?\",\"expected_output\":\"Csv answer\"}]}",
                "{\"input\":\"Csv question?\"}",
                "Generated CSV answer"));
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
        assertTrue(generated.contains("\"expected_output\" : \"Generated CSV answer\""));
    }

    @Test
    void generateGoldensDefaultsMaxGoldensPerGoldenToTwoLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("goldens.json");
        Files.writeString(goldens, """
                [
                  {"input":"Old question?","context":["Paris is in France."]}
                ]
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses,
                "{\"data\":[{\"input\":\"Question one?\",\"expected_output\":\"Answer one\"},{\"input\":\"Question two?\",\"expected_output\":\"Answer two\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "single-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "default-max-goldens"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("default-max-goldens.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("Question two?"));
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
    void generateRequiresMethodBeforeProviderSetupLikeDeepEval() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--variation", "single-turn",
                "--scenario", "users", "--task", "answer", "--input-format", "question",
                "--num-goldens", "1"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--method is required"));
        assertFalse(text(err).contains("No supported provider"));
    }

    @Test
    void generateRejectsUnsupportedMethodBeforeProviderSetupLikeDeepEval() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "invalid", "--variation", "single-turn",
                "--scenario", "users", "--task", "answer", "--input-format", "question",
                "--num-goldens", "1"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing or unsupported --method."));
        assertFalse(text(err).contains("No supported provider"));
    }

    @Test
    void generateRejectsUnsupportedFileTypeBeforeProviderSetupLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--file-type", "xml"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid file type. Available file types to save as: json, csv, jsonl"));
        assertFalse(text(err).contains("No supported provider"));
    }

    @Test
    void generateRejectsUnknownOptionsLikeDeepEvalTyper() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"refund policy\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Refund?\",\"expected_output\":\"Yes\"}]}");
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "unknown-option",
                "--max-golden-per-context", "1"
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --max-golden-per-context"));
    }

    @Test
    void generateRejectsMissingOptionValuesLikeDeepEvalTyper() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Refund?\",\"expected_output\":\"Yes\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts", "--variation", "single-turn",
                "--contexts-file", "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --contexts-file"));
    }

    @Test
    void generateRequiresVariationLikeDeepEval() throws Exception {
        var contexts = tempDir.resolve("contexts.json");
        Files.writeString(contexts, "[[\"Paris is in France.\"]]");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Capital?\",\"expected_output\":\"Paris\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "contexts",
                "--contexts-file", contexts.toString(), "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--variation is required"));
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

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("Question two?"));
        assertTrue(generated.contains("policy.md"));
    }

    @Test
    void generateDocsDoesNotConsumeNextBatchResponseForExpectedOutputFallback() throws Exception {
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
                "--file-name", "docs-expected-fallback"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-expected-fallback.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("Question two?"));
        assertTrue(generated.contains("\"expected_output\" : \"Generated expected output\""));
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
    void generateDocsAcceptsDocumentsEqualsFormLikeDeepEvalTyper() throws Exception {
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
                "--documents=" + document, "--chunk-size", "2",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "docs-equals-alias"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("docs-equals-alias.json"));
        assertTrue(generated.contains("Question one?"));
        assertTrue(generated.contains("policy.md"));
    }

    @Test
    void generateDocsSupportsCrossFileContextOptionsLikeDeepEval() throws Exception {
        var policy = tempDir.resolve("policy.md");
        var faq = tempDir.resolve("faq.md");
        Files.writeString(policy, "policy refund");
        Files.writeString(faq, "faq timeline");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"How do policy and FAQ connect?","expected_output":"Use both files","used_source_files":["policy.md","faq.md"]}]}
                """);
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--documents", policy.toString(), "--documents", faq.toString(),
                "--chunk-size", "2", "--max-contexts-per-document", "1",
                "--allow-cross-file-contexts", "--target-files-per-context", "2", "--max-files-per-context", "2",
                "--responses-file", responses.toString(), "--output-dir", output.toString(),
                "--file-name", "cross-docs"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("cross-docs.json"));
        assertTrue(generated.contains("How do policy and FAQ connect?"));
        assertTrue(generated.contains("policy.md"));
        assertTrue(generated.contains("faq.md"));
        assertTrue(generated.contains("used_source_files"));
    }

    @Test
    void generateDocsValidatesCrossFileTargetCountLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "policy refund");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[{\"input\":\"Question?\"}]}");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(),
                "--allow-cross-file-contexts", "--target-files-per-context", "1",
                "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("target_files_per_context must be at least 2 when provided."));
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
    void generateDocsValidatesChunkOverlapLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(), "--chunk-size", "3", "--chunk-overlap", "3",
                "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("`chunk_overlap` must not exceed 2 (chunk_size - 1)."));
    }

    @Test
    void generateDocsValidatesMinContextsPerDocumentLikeDeepEval() throws Exception {
        var document = tempDir.resolve("policy.md");
        Files.writeString(document, "alpha beta gamma");
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, """
                {"data":[{"input":"Question one?","expected_output":"Answer one"}]}
                """);
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "docs", "--variation", "single-turn",
                "--document-path", document.toString(), "--chunk-size", "10",
                "--min-contexts-per-document", "2",
                "--responses-file", responses.toString()
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Impossible to generate 2 contexts from a document with 1 chunks."));
        assertTrue(text(err).contains("Adjust the `min_contexts_per_document` to no more than 1."));
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
        assertTrue(generated.contains("\"expected_outcome\" : \"Generated conversational expected outcome\""));
    }

    @Test
    void generateCreatesConversationalStylingFromExpectedOutcomeFormatOnlyLikeDeepEval() throws Exception {
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, "{\"data\":[]}");
        var method = GenerateCommand.class.getDeclaredMethod("synthesizer", String[].class);
        method.setAccessible(true);

        var synthesizer = method.invoke(null, (Object) new String[] {
                "generate", "--method", "contexts", "--variation", "multi-turn",
                "--contexts-file", tempDir.resolve("contexts.json").toString(),
                "--expected-outcome-format", "Return a numbered checklist.",
                "--responses-file", responses.toString()
        });
        var field = synthesizer.getClass().getDeclaredField("conversationalStylingConfig");
        field.setAccessible(true);
        var stylingConfig = field.get(synthesizer);

        assertTrue(stylingConfig instanceof dev.jeval.synthesizer.ConversationalStylingConfig);
        assertEquals("Return a numbered checklist.",
                ((dev.jeval.synthesizer.ConversationalStylingConfig) stylingConfig).expectedOutcomeFormat());
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
        Files.writeString(responses, String.join(System.lineSeparator(),
                "{\"scenario_context\":\"travel support\",\"conversational_task\":\"change flights\",\"participant_roles\":\"traveler and agent\"}",
                "{\"data\":[{\"scenario\":\"flight change request\",\"turns\":[{\"role\":\"user\",\"content\":\"Change my flight\"}],\"expected_outcome\":\"Flight change started\"}]}",
                "{\"scenario\":\"flight change request\"}"));
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
    void generateMultiTurnGoldensDefaultsMaxGoldensPerGoldenToTwoLikeDeepEval() throws Exception {
        var goldens = tempDir.resolve("goldens.json");
        Files.writeString(goldens, """
                [
                  {"scenario":"traveler wants to rebook a flight","context":["Flights can be changed online."]}
                ]
                """);
        var responses = tempDir.resolve("responses.txt");
        Files.writeString(responses, String.join(System.lineSeparator(),
                "{\"scenario_context\":\"travel support\",\"conversational_task\":\"change flights\",\"participant_roles\":\"traveler and agent\"}",
                "{\"data\":[{\"scenario\":\"first scenario\",\"turns\":[{\"role\":\"user\",\"content\":\"One\"}],\"expected_outcome\":\"First outcome\"},{\"scenario\":\"second scenario\",\"turns\":[{\"role\":\"user\",\"content\":\"Two\"}],\"expected_outcome\":\"Second outcome\"}]}",
                "{\"scenario\":\"first scenario\"}",
                "First outcome",
                "{\"scenario\":\"second scenario\"}",
                "Second outcome"));
        var output = tempDir.resolve("generated");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "generate", "--method", "goldens", "--variation", "multi-turn",
                "--goldens-file", goldens.toString(), "--responses-file", responses.toString(),
                "--output-dir", output.toString(), "--file-name", "default-max-conv-goldens"
        }, out, err);

        assertEquals(0, exit, text(err));
        var generated = Files.readString(output.resolve("default-max-conv-goldens.json"));
        assertTrue(generated.contains("first scenario"));
        assertTrue(generated.contains("second scenario"));
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
    void settingsUnsetAcceptsPartialSettingFilter() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings",
                "--set", "openai-api-key=sk-openai",
                "--set", "anthropic-api-key=sk-anthropic",
                "--set", "log-level=info",
                "--save", "dotenv:" + env
        }, out, err), text(err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"settings", "--unset", "api-key", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, exit, text(err));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_API_KEY"));
        assertEquals(false, readDotenv(env).containsKey("ANTHROPIC_API_KEY"));
        assertDotenv(env, "LOG_LEVEL", "20");
    }

    @Test
    void settingsUnsetReturnsErrorWhenFilterMatchesNothing() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "--set", "log-level=info", "--save", "dotenv:" + env
        }, out, err), text(err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"settings", "--unset", "api-key", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No settings matched"));
        assertDotenv(env, "LOG_LEVEL", "20");
    }

    @Test
    void settingsAcceptsDeepEvalSetAlias() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "temperature=0.42", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "TEMPERATURE", "0.42");
    }

    @Test
    void settingsRejectsMissingSetValueBeforeConsumingNextOption() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"settings", "--set", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --set"));
    }

    @Test
    void settingsRejectsUnknownOptionsLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"settings", "--missing-option", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --missing-option"));
    }

    @Test
    void settingsRejectsUnknownSettingLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"settings", "--set", "not-a-setting=value", "--save", "dotenv:" + env},
                out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Unknown setting: 'not-a-setting'"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsSetWithoutEqualsLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"settings", "--set", "temperature", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("--set must be KEY=VALUE (got 'temperature')"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidTemperatureLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"settings", "--set", "temperature=hot", "--save", "dotenv:" + env},
                out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for TEMPERATURE: hot"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidTraceSampleRateLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "confident-trace-sample-rate=2", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for CONFIDENT_TRACE_SAMPLE_RATE: 2"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidRetryMaxAttemptsLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-retry-max-attempts=0", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_RETRY_MAX_ATTEMPTS: 0"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidRetryExpBaseLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-retry-exp-base=0", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_RETRY_EXP_BASE: 0"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidRetryInitialSecondsLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-retry-initial-seconds=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_RETRY_INITIAL_SECONDS: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidRetryJitterLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-retry-jitter=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_RETRY_JITTER: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidRetryCapSecondsLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-retry-cap-seconds=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_RETRY_CAP_SECONDS: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidTimeoutSemaphoreWarnLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-timeout-semaphore-warn-after-seconds=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_TIMEOUT_SEMAPHORE_WARN_AFTER_SECONDS: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidTimeoutThreadLimitLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-timeout-thread-limit=0", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_TIMEOUT_THREAD_LIMIT: 0"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidPerAttemptTimeoutOverrideLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-per-attempt-timeout-seconds-override=0", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE: 0"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidPerTaskTimeoutOverrideLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-per-task-timeout-seconds-override=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsRejectsInvalidTaskGatherBufferOverrideLikeDeepEval() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--set", "deepeval-task-gather-buffer-seconds-override=-1", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE: -1"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void settingsAcceptsEqualsFormForDeepEvalAliases() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var setExit = run(new String[] {
                "settings", "--set=log-level=info", "--set=anthropic-api-key=sk-test", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(0, setExit, text(err));
        assertDotenv(env, "LOG_LEVEL", "20");
        assertDotenv(env, "ANTHROPIC_API_KEY", "sk-test");

        out.reset();
        err.reset();
        var listExit = run(new String[] {"settings", "--list=anthropic", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, listExit, text(err));
        assertTrue(text(out).contains("ANTHROPIC_API_KEY=********"));
        assertEquals(false, text(out).contains("LOG_LEVEL="));

        out.reset();
        err.reset();
        var unsetExit = run(new String[] {"settings", "--unset=anthropic", "--save", "dotenv:" + env}, out, err);

        assertEquals(0, unsetExit, text(err));
        assertDotenv(env, "LOG_LEVEL", "20");
        assertEquals(false, readDotenv(env).containsKey("ANTHROPIC_API_KEY"));
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
    void settingsListAcceptsPositionalFilterAfterOptions() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "--set", "log-level=info", "--set", "anthropic-api-key=sk-test",
                "--save", "dotenv:" + env
        }, out, err), text(err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"settings", "--list", "--save", "dotenv:" + env, "anthropic"},
                out, err);

        assertEquals(0, exit, text(err));
        assertTrue(text(out).contains("ANTHROPIC_API_KEY=********"));
        assertEquals(false, text(out).contains("LOG_LEVEL="));
    }

    @Test
    void settingsListReturnsErrorWhenFilterMatchesNothing() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "--set", "log-level=info", "--save", "dotenv:" + env
        }, out, err), text(err));

        out.reset();
        err.reset();
        var exit = run(new String[] {"settings", "--list", "anthropic", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertEquals("", text(out));
        assertTrue(text(err).contains("No settings matched"));
    }

    @Test
    void settingsListRejectsSetOrUnsetInSameCommand() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings", "--list", "--set", "temperature=0.42", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Cannot use --list with --set or --unset"));
        assertEquals(false, Files.exists(env));
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
    void settingsSaveEqualsDotenvWritesDefaultDotenvLocalLikeDeepEval() throws Exception {
        withDefaultDotenvLocal("", () -> {
            Files.deleteIfExists(Path.of(".env.local"));
            Files.deleteIfExists(Path.of("dotenv"));
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();

            var exit = run(new String[] {"settings", "-u", "log-level=info", "--save=dotenv"}, out, err);

            assertEquals(0, exit, text(err));
            assertDotenv(Path.of(".env.local"), "LOG_LEVEL", "20");
            assertEquals(false, Files.exists(Path.of("dotenv")));
        });
    }

    @Test
    void settingsCanonicalizesDeepEvalDeprecatedComputedTimeoutAliases() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings",
                "-u", "deepeval-per-task-timeout-seconds=42",
                "-u", "deepeval-per-attempt-timeout-seconds=5",
                "-u", "deepeval-task-gather-buffer-seconds=12",
                "--save", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE", "42");
        assertDotenv(env, "DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE", "5");
        assertDotenv(env, "DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE", "12");
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_PER_TASK_TIMEOUT_SECONDS"));
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS"));
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_TASK_GATHER_BUFFER_SECONDS"));
    }

    @Test
    void settingsExplicitTimeoutOverrideWinsOverDeprecatedAlias() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "settings",
                "-u", "deepeval-per-task-timeout-seconds=999",
                "-u", "deepeval-per-task-timeout-seconds-override=7",
                "--save", "dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE", "7");
        assertEquals(false, readDotenv(env).containsKey("DEEPEVAL_PER_TASK_TIMEOUT_SECONDS"));

        var reversedEnv = tempDir.resolve(".env.reversed");
        out.reset();
        err.reset();

        exit = run(new String[] {
                "settings",
                "-u", "deepeval-per-task-timeout-seconds-override=7",
                "-u", "deepeval-per-task-timeout-seconds=999",
                "--save", "dotenv:" + reversedEnv
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(reversedEnv, "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE", "7");
        assertEquals(false, readDotenv(reversedEnv).containsKey("DEEPEVAL_PER_TASK_TIMEOUT_SECONDS"));
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
    void setDebugPersistsCriticalAndNotsetLogLevelsLikePythonLogging() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-debug",
                "--log-level", "CRITICAL",
                "--retry-before-level", "NOTSET",
                "--save", "dotenv:" + env,
                "--quiet"
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "LOG_LEVEL", "50");
        assertDotenv(env, "DEEPEVAL_RETRY_BEFORE_LOG_LEVEL", "0");
    }

    @Test
    void setDebugRejectsUnknownOptionsLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-debug", "--missing-option", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --missing-option"));
    }

    @Test
    void setDebugRejectsMissingLogLevelValueBeforeConsumingNextOption() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-debug", "--log-level", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --log-level"));
    }

    @Test
    void setDebugRejectsInvalidTraceSampleRateLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-debug", "--trace-sample-rate", "often", "--save", "dotenv:" + env},
                out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for --trace-sample-rate: often"));
        assertEquals(false, Files.exists(env));
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
    void unsetDebugRejectsUnknownOptionsLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"unset-debug", "--missing-option", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --missing-option"));
    }

    @Test
    void unsetDebugRejectsMissingSaveValueBeforeConsumingNextOption() {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"unset-debug", "--save", "--quiet"}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --save"));
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
    void providerAcceptsEqualsFormForDeepEvalOptions() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-ollama", "--model=llama3", "--base-url=http://localhost:11434", "--save=dotenv:" + env
        }, out, err);

        assertEquals(0, exit, text(err));
        assertDotenv(env, "USE_LOCAL_MODEL", "YES");
        assertDotenv(env, "OLLAMA_MODEL_NAME", "llama3");
        assertDotenv(env, "LOCAL_MODEL_BASE_URL", "http://localhost:11434");
    }

    @Test
    void providerRejectsMissingOptionValueBeforeConsumingNextOption() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-openai", "--model", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Missing value for --model"));
    }

    @Test
    void providerRejectsUnknownOptionsLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--missing-option", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --missing-option"));
    }

    @Test
    void providerSetRejectsUnsetOnlyClearSecretsOption() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--clear-secrets", "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --clear-secrets"));
    }

    @Test
    void providerUnsetRejectsSetOnlyOptionsLikeDeepEvalTyper() {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"unset-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("No such option: --model"));
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
    void setOpenAiRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-openai", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("OpenAI model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setAzureOpenAiRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-azure-openai", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Azure OpenAI model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setAnthropicRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-anthropic", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Anthropic model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setBedrockRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-bedrock", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("AWS Bedrock model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setGeminiRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-gemini", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Gemini model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setGrokRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-grok", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Grok model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setMoonshotRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-moonshot", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Moonshot model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setDeepSeekRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-deepseek", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("DeepSeek model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setLiteLlmRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-litellm", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("LiteLLM model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setPortkeyRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-portkey", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Portkey model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setOpenRouterRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-openrouter", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("OpenRouter model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setOllamaRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-ollama", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Ollama model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setLocalModelRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-local-model", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Local model name is not set"));
        assertEquals(false, Files.exists(env));
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
    void unsetProviderAcceptsClearSecretsShortAlias() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "settings", "-u", "openai-api-key=sk-test", "--save", "dotenv:" + env
        }, out, err), text(err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err), text(err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-openai", "-x", "--save", "dotenv:" + env}, out, err),
                text(err));
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
    void bedrockProviderRoundtripUsesExclusiveFlagsAndRegion() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-bedrock", "--model", "amazon.nova-lite-v1:0",
                "--region", "us-west-2", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_AWS_BEDROCK_MODEL", "YES");
        assertDotenv(env, "AWS_BEDROCK_MODEL_NAME", "amazon.nova-lite-v1:0");
        assertDotenv(env, "AWS_BEDROCK_REGION", "us-west-2");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-bedrock", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_AWS_BEDROCK_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("AWS_BEDROCK_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("AWS_BEDROCK_REGION"));
    }

    @Test
    void providerCostOverridesUseProviderSpecificKeysLikeDeepEval() throws Exception {
        assertProviderCostOverrides(
                "set-anthropic",
                new String[] {"--model", "claude-3-5-sonnet"},
                "unset-anthropic",
                "ANTHROPIC_COST_PER_INPUT_TOKEN",
                "ANTHROPIC_COST_PER_OUTPUT_TOKEN");
        assertProviderCostOverrides(
                "set-bedrock",
                new String[] {"--model", "anthropic.claude", "--region", "us-east-1"},
                "unset-bedrock",
                "AWS_BEDROCK_COST_PER_INPUT_TOKEN",
                "AWS_BEDROCK_COST_PER_OUTPUT_TOKEN");
        assertProviderCostOverrides(
                "set-grok",
                new String[] {"--model", "grok-4.3"},
                "unset-grok",
                "GROK_COST_PER_INPUT_TOKEN",
                "GROK_COST_PER_OUTPUT_TOKEN");
        assertProviderCostOverrides(
                "set-moonshot",
                new String[] {"--model", "kimi-k2.6"},
                "unset-moonshot",
                "MOONSHOT_COST_PER_INPUT_TOKEN",
                "MOONSHOT_COST_PER_OUTPUT_TOKEN");
        assertProviderCostOverrides(
                "set-deepseek",
                new String[] {"--model", "deepseek-chat"},
                "unset-deepseek",
                "DEEPSEEK_COST_PER_INPUT_TOKEN",
                "DEEPSEEK_COST_PER_OUTPUT_TOKEN");
    }

    @Test
    void providerCostOverridesAcceptDeepEvalShortAliases() throws Exception {
        assertProviderCostShortAliases(
                "set-openai",
                new String[] {"--model", "gpt-4o-mini"},
                "OPENAI_COST_PER_INPUT_TOKEN",
                "OPENAI_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-openrouter",
                new String[] {"--model", "openai/gpt-4.1"},
                "OPENROUTER_COST_PER_INPUT_TOKEN",
                "OPENROUTER_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-anthropic",
                new String[] {"--model", "claude-3-5-sonnet"},
                "ANTHROPIC_COST_PER_INPUT_TOKEN",
                "ANTHROPIC_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-bedrock",
                new String[] {"--model", "anthropic.claude", "--region", "us-east-1"},
                "AWS_BEDROCK_COST_PER_INPUT_TOKEN",
                "AWS_BEDROCK_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-grok",
                new String[] {"--model", "grok-4.3"},
                "GROK_COST_PER_INPUT_TOKEN",
                "GROK_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-moonshot",
                new String[] {"--model", "kimi-k2.6"},
                "MOONSHOT_COST_PER_INPUT_TOKEN",
                "MOONSHOT_COST_PER_OUTPUT_TOKEN");
        assertProviderCostShortAliases(
                "set-deepseek",
                new String[] {"--model", "deepseek-chat"},
                "DEEPSEEK_COST_PER_INPUT_TOKEN",
                "DEEPSEEK_COST_PER_OUTPUT_TOKEN");
    }

    @Test
    void providerRejectsInvalidCostOverrideValuesLikeDeepEvalTyper() throws Exception {
        var env = tempDir.resolve("invalid-cost.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-openai",
                "--model", "gpt-4o-mini",
                "--cost-per-input-token", "expensive",
                "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for --cost-per-input-token: expensive"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void providerRejectsInvalidTemperatureValueLikeDeepEvalTyper() throws Exception {
        var env = tempDir.resolve("invalid-temperature.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {
                "set-openai",
                "--model", "gpt-4o-mini",
                "--temperature", "warm",
                "--save", "dotenv:" + env
        }, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Invalid value for --temperature: warm"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void openRouterProviderAcceptsDeepEvalTemperatureShortAlias() throws Exception {
        var env = tempDir.resolve("openrouter-temperature.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openrouter", "--model", "openai/gpt-4.1", "-t", "0.25",
                "--save", "dotenv:" + env
        }, out, err), text(err));

        assertDotenv(env, "TEMPERATURE", "0.25");
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
    void grokProviderRoundtripUsesExclusiveFlagsAndBaseUrl() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-grok", "--model", "grok-4.3",
                "--base-url", "https://api.x.ai/v1", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_GROK_MODEL", "YES");
        assertDotenv(env, "GROK_MODEL_NAME", "grok-4.3");
        assertDotenv(env, "GROK_BASE_URL", "https://api.x.ai/v1");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-grok", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_GROK_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("GROK_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("GROK_BASE_URL"));
    }

    @Test
    void moonshotProviderRoundtripUsesExclusiveFlagsAndBaseUrl() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-moonshot", "--model", "kimi-k2.6",
                "--base-url", "https://api.moonshot.ai/v1", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_MOONSHOT_MODEL", "YES");
        assertDotenv(env, "MOONSHOT_MODEL_NAME", "kimi-k2.6");
        assertDotenv(env, "MOONSHOT_BASE_URL", "https://api.moonshot.ai/v1");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-moonshot", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_MOONSHOT_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("MOONSHOT_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("MOONSHOT_BASE_URL"));
    }

    @Test
    void liteLlmProviderRoundtripUsesExclusiveFlagsAndBaseUrl() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-litellm", "--model", "openai/gpt-4.1",
                "--base-url", "http://localhost:4000", "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_LITELLM", "YES");
        assertDotenv(env, "LITELLM_MODEL_NAME", "openai/gpt-4.1");
        assertDotenv(env, "LITELLM_API_BASE", "http://localhost:4000");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-litellm", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_LITELLM"));
        assertEquals(false, readDotenv(env).containsKey("LITELLM_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("LITELLM_API_BASE"));
    }

    @Test
    void portkeyProviderRoundtripUsesExclusiveFlagsBaseUrlAndProvider() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-openai", "--model", "gpt-4o-mini", "--save", "dotenv:" + env
        }, out, err));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-portkey", "--model", "gpt-4.1",
                "--base-url", "https://api.portkey.ai/v1",
                "--provider", "openai-prod",
                "--save", "dotenv:" + env
        }, out, err));
        assertDotenv(env, "USE_PORTKEY_MODEL", "YES");
        assertDotenv(env, "PORTKEY_MODEL_NAME", "gpt-4.1");
        assertDotenv(env, "PORTKEY_BASE_URL", "https://api.portkey.ai/v1");
        assertDotenv(env, "PORTKEY_PROVIDER_NAME", "openai-prod");
        assertEquals(false, readDotenv(env).containsKey("USE_OPENAI_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("OPENAI_MODEL_NAME"));

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-portkey", "--save", "dotenv:" + env}, out, err));
        assertEquals(false, readDotenv(env).containsKey("USE_PORTKEY_MODEL"));
        assertEquals(false, readDotenv(env).containsKey("PORTKEY_MODEL_NAME"));
        assertEquals(false, readDotenv(env).containsKey("PORTKEY_BASE_URL"));
        assertEquals(false, readDotenv(env).containsKey("PORTKEY_PROVIDER_NAME"));
    }

    @Test
    void providerEmbeddingCommandsAcceptDeepEvalShortAliases() throws Exception {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var litellm = tempDir.resolve("litellm-short.env");
        assertEquals(0, run(new String[] {
                "set-litellm", "-m", "openai/gpt-4.1", "-u", "http://localhost:4000",
                "-U", "http://localhost:4001", "--save", "dotenv:" + litellm
        }, out, err), text(err));
        assertDotenv(litellm, "LITELLM_MODEL_NAME", "openai/gpt-4.1");
        assertDotenv(litellm, "LITELLM_API_BASE", "http://localhost:4000");
        assertDotenv(litellm, "LITELLM_PROXY_API_BASE", "http://localhost:4001");

        var portkey = tempDir.resolve("portkey-short.env");
        assertEquals(0, run(new String[] {
                "set-portkey", "-m", "gpt-4.1", "-u", "https://api.portkey.ai/v1",
                "-P", "openai-prod", "--save", "dotenv:" + portkey
        }, out, err), text(err));
        assertDotenv(portkey, "PORTKEY_MODEL_NAME", "gpt-4.1");
        assertDotenv(portkey, "PORTKEY_BASE_URL", "https://api.portkey.ai/v1");
        assertDotenv(portkey, "PORTKEY_PROVIDER_NAME", "openai-prod");

        var azure = tempDir.resolve("azure-embedding-short.env");
        assertEquals(0, run(new String[] {
                "set-azure-openai-embedding", "-m", "text-embedding-3-large",
                "-d", "embedding-prod", "--save", "dotenv:" + azure
        }, out, err), text(err));
        assertDotenv(azure, "AZURE_EMBEDDING_MODEL_NAME", "text-embedding-3-large");
        assertDotenv(azure, "AZURE_EMBEDDING_DEPLOYMENT_NAME", "embedding-prod");

        var local = tempDir.resolve("local-embedding-short.env");
        assertEquals(0, run(new String[] {
                "set-local-embeddings", "-m", "nomic-embed-text", "-u", "http://localhost:11434",
                "--save", "dotenv:" + local
        }, out, err), text(err));
        assertDotenv(local, "LOCAL_EMBEDDING_MODEL_NAME", "nomic-embed-text");
        assertDotenv(local, "LOCAL_EMBEDDING_BASE_URL", "http://localhost:11434");
    }

    @Test
    void setAzureOpenAiEmbeddingRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-azure-openai-embedding", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Azure OpenAI embedding model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setLocalEmbeddingsRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-local-embeddings", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Local embedding model name is not set"));
        assertEquals(false, Files.exists(env));
    }

    @Test
    void setOllamaEmbeddingsRequiresConfiguredModel() throws Exception {
        var env = tempDir.resolve(".env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var exit = run(new String[] {"set-ollama-embeddings", "--save", "dotenv:" + env}, out, err);

        assertEquals(2, exit);
        assertTrue(text(err).contains("Local embedding model name is not set"));
        assertEquals(false, Files.exists(env));
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

    @Test
    void ollamaEmbeddingsSetDeepEvalLocalApiKeySentinel() throws Exception {
        var env = tempDir.resolve("ollama-embeddings.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        assertEquals(0, run(new String[] {
                "set-ollama-embeddings",
                "--model", "nomic-embed-text",
                "--base-url", "http://localhost:11434",
                "--save", "dotenv:" + env
        }, out, err), text(err));
        assertDotenv(env, "USE_LOCAL_EMBEDDINGS", "YES");
        assertDotenv(env, "LOCAL_EMBEDDING_MODEL_NAME", "nomic-embed-text");
        assertDotenv(env, "LOCAL_EMBEDDING_BASE_URL", "http://localhost:11434");
        assertDotenv(env, "LOCAL_EMBEDDING_API_KEY", "ollama");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {"unset-ollama-embeddings", "--save", "dotenv:" + env}, out, err),
                text(err));
        assertEquals(false, readDotenv(env).containsKey("USE_LOCAL_EMBEDDINGS"));
        assertEquals(false, readDotenv(env).containsKey("LOCAL_EMBEDDING_MODEL_NAME"));
        assertDotenv(env, "LOCAL_EMBEDDING_API_KEY", "ollama");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "set-ollama-embeddings",
                "--model", "nomic-embed-text",
                "--save", "dotenv:" + env
        }, out, err), text(err));
        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {
                "unset-ollama-embeddings", "--clear-secrets", "--save", "dotenv:" + env
        }, out, err), text(err));
        assertEquals(false, readDotenv(env).containsKey("LOCAL_EMBEDDING_API_KEY"));
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

    private void assertProviderCostOverrides(
            String setCommand,
            String[] baseArgs,
            String unsetCommand,
            String inputKey,
            String outputKey) throws Exception {
        var env = tempDir.resolve(setCommand + "-costs.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var setArgs = new java.util.ArrayList<String>();
        setArgs.addAll(java.util.List.of(baseArgs));
        setArgs.add("--cost-per-input-token");
        setArgs.add("0.00011");
        setArgs.add("--cost-per-output-token");
        setArgs.add("0.00022");

        assertEquals(0, run(providerArgs(setCommand, setArgs.toArray(String[]::new), env), out, err), text(err));
        assertDotenv(env, inputKey, "0.00011");
        assertDotenv(env, outputKey, "0.00022");

        out.reset();
        err.reset();
        assertEquals(0, run(new String[] {unsetCommand, "--save", "dotenv:" + env}, out, err), text(err));
        assertEquals(false, readDotenv(env).containsKey(inputKey));
        assertEquals(false, readDotenv(env).containsKey(outputKey));
    }

    private void assertProviderCostShortAliases(
            String setCommand,
            String[] baseArgs,
            String inputKey,
            String outputKey) throws Exception {
        var env = tempDir.resolve(setCommand + "-short-costs.env");
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();

        var setArgs = new java.util.ArrayList<String>();
        setArgs.add(setCommand);
        setArgs.addAll(java.util.List.of(baseArgs));
        setArgs.add("-i");
        setArgs.add("0.00031");
        setArgs.add("-o");
        setArgs.add("0.00062");
        setArgs.add("--save");
        setArgs.add("dotenv:" + env);

        assertEquals(0, run(setArgs.toArray(String[]::new), out, err), text(err));
        assertDotenv(env, inputKey, "0.00031");
        assertDotenv(env, outputKey, "0.00062");
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

    private static void withDefaultDotenvLocal(String content, CheckedRunnable action) throws Exception {
        var path = Path.of(".env.local");
        var literalDotenv = Path.of("dotenv");
        var existed = Files.exists(path);
        var original = existed ? Files.readString(path) : null;
        var literalExisted = Files.exists(literalDotenv);
        var literalOriginal = literalExisted ? Files.readString(literalDotenv) : null;
        Files.writeString(path, content);
        try {
            action.run();
        } finally {
            if (existed) {
                Files.writeString(path, original);
            } else {
                Files.deleteIfExists(path);
            }
            if (literalExisted) {
                Files.writeString(literalDotenv, literalOriginal);
            } else {
                Files.deleteIfExists(literalDotenv);
            }
        }
    }

    private static String text(ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    private static Object accessor(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Path generatedPathFromMessage(String message) {
        var prefix = "Synthetic goldens saved at ";
        assertTrue(message.startsWith(prefix));
        assertTrue(message.trim().endsWith("!"));
        return Path.of(message.trim().substring(prefix.length(), message.trim().length() - 1));
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
