package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void runsJsonEvaluationFileWithoutTestPrefix() throws Exception {
        var file = tempDir.resolve("judgment_eval.json");
        Files.writeString(file, """
                {
                  "name": "judgment",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "pass", "input": "q1", "actualOutput": "yes", "expectedOutput": "yes"},
                    {"name": "fail", "input": "q2", "actualOutput": "no", "expectedOutput": "yes"}
                  ]
                }
                """);

        var result = new TestRunner().run(file);

        assertEquals("judgment", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
        assertEquals(0.5, result.summary().averageScore());
        assertEquals(0.5, result.summary().passRate());
        assertFalse(result.success());
    }

    @Test
    void inlineCasesAcceptAdditionalMetadataAliasLikeDatasets() throws Exception {
        var file = tempDir.resolve("metadata_eval.json");
        Files.writeString(file, """
                {
                  "name": "metadata",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"input": "q", "actualOutput": "yes", "expectedOutput": "yes",
                     "additional_metadata": {"suite": "legacy"}}
                  ]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(file));

        assertEquals("legacy", result.results().getFirst().metadata().get("suite"));
    }

    @Test
    void runsJsonlDatasetFromSpec() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q1","actual_output":"yes","expected_output":"yes"}
                {"input":"q2","actual_output":"no","expected_output":"yes"}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = new TestRunner().run(spec);

        assertEquals("jsonl-run", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
    }

    @Test
    void jsonlDatasetAcceptsCamelCaseOutputAliasesLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q1","actualOutput":"yes","expectedOutput":"yes"}
                {"input":"q2","actualOutput":"no","expectedOutput":"yes"}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));

        assertEquals("jsonl-run", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
    }

    @Test
    void jsonDatasetAcceptsCamelCaseOutputAliasesLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.json");
        Files.writeString(dataset, """
                [
                  {"input":"q1","actualOutput":"yes","expectedOutput":"yes"},
                  {"input":"q2","actualOutput":"no","expectedOutput":"yes"}
                ]
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "json-run",
                  "dataset": "cases.json",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));

        assertEquals("json-run", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
    }

    @Test
    void jsonlDatasetAcceptsCamelCaseRetrievalContextAliasLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q","actualOutput":"yes","expectedOutput":"yes","retrievalContext":["retrieved fact"]}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));

        assertEquals(List.of("retrieved fact"), result.results().getFirst().retrievalContext());
    }

    @Test
    void jsonlDatasetAcceptsCamelCaseNumericAliasesLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q","actualOutput":"yes","expectedOutput":"yes","tokenCost":0.42,"completionTime":2.5}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));

        assertEquals(0.42, result.results().getFirst().tokenCost());
        assertEquals(2.5, result.results().getFirst().completionTime());
    }

    @Test
    void jsonlDatasetAcceptsCamelCaseComplexAliasesLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q","actualOutput":"yes","expectedOutput":"yes","customColumnKeyValues":{"risk":"high"},"toolsCalled":[{"name":"Search"}],"expectedTools":[{"name":"Search"}],"mcpServers":[{"server_name":"policy"}],"mcpToolsCalled":[{"name":"mcp-search"}],"mcpResourcesCalled":[{"uri":"file://policy"}],"mcpPromptsCalled":[{"name":"policy-prompt"}]}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));
        var testCase = result.results().getFirst();

        assertEquals(java.util.Map.of("risk", "high"), testCase.customColumnKeyValues());
        assertEquals("Search", testCase.toolsCalled().getFirst().name());
        assertEquals("Search", testCase.expectedTools().getFirst().name());
        assertEquals(List.of(java.util.Map.of("server_name", "policy")), testCase.mcpServers());
        assertEquals(List.of(java.util.Map.of("name", "mcp-search")), testCase.mcpToolsCalled());
        assertEquals(List.of(java.util.Map.of("uri", "file://policy")), testCase.mcpResourcesCalled());
        assertEquals(List.of(java.util.Map.of("name", "policy-prompt")), testCase.mcpPromptsCalled());
    }

    @Test
    void jsonlDatasetRejectsInvalidNumericFieldsLikeDeepEval() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q","actual_output":"yes","expected_output":"yes","token_cost":"expensive"}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("Invalid value for token_cost: expensive"));
    }

    @Test
    void jsonlDatasetRejectsNonFiniteNumericFields() throws Exception {
        var dataset = tempDir.resolve("cases.jsonl");
        Files.writeString(dataset, """
                {"input":"q","actual_output":"yes","expected_output":"yes","token_cost":"NaN"}
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "jsonl-run",
                  "dataset": "cases.jsonl",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("Invalid value for token_cost: NaN"));
    }

    @Test
    void rejectsNonFiniteMetricThresholds() throws Exception {
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "bad-threshold",
                  "metrics": [{"type": "exact_match", "threshold": 1e999}],
                  "cases": [{"input": "q", "actualOutput": "yes", "expectedOutput": "yes"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("Invalid value for threshold"));
    }

    @Test
    void rejectsMetricSpecsWithoutType() throws Exception {
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "bad-metric",
                  "metrics": [{"threshold": 0.5}],
                  "cases": [{"input": "q", "actualOutput": "yes", "expectedOutput": "yes"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("Metric spec must define type"));
    }

    @Test
    void rejectsPatternMatchMetricWithoutPattern() throws Exception {
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "bad-pattern",
                  "metrics": [{"type": "pattern_match"}],
                  "cases": [{"input": "q", "actualOutput": "yes"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("Pattern match metric requires pattern"));
    }

    @Test
    void runsCsvDatasetFromSpec() throws Exception {
        var dataset = tempDir.resolve("cases.csv");
        Files.writeString(dataset, """
                input,actual_output,expected_output
                q1,yes,yes
                q2,no,yes
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "csv-run",
                  "dataset": "cases.csv",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = new TestRunner().run(spec);

        assertEquals("csv-run", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
    }

    @Test
    void csvDatasetAcceptsCamelCaseOutputAliasesLikeInlineCases() throws Exception {
        var dataset = tempDir.resolve("cases.csv");
        Files.writeString(dataset, """
                input,actualOutput,expectedOutput
                q1,yes,yes
                q2,no,yes
                """);
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "csv-run",
                  "dataset": "cases.csv",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var result = assertDoesNotThrow(() -> new TestRunner().run(spec));

        assertEquals("csv-run", result.name());
        assertEquals(2, result.results().size());
        assertEquals(1, result.summary().passed());
        assertEquals(1, result.summary().failed());
    }

    @Test
    void rejectsSpecWithoutCasesOrDataset() throws Exception {
        var spec = tempDir.resolve("eval.json");
        Files.writeString(spec, """
                {
                  "name": "empty",
                  "metrics": [{"type": "exact_match"}]
                }
                """);

        var error = assertThrows(IllegalArgumentException.class, () -> new TestRunner().run(spec));

        assertTrue(error.getMessage().contains("cases or dataset"));
    }
}
