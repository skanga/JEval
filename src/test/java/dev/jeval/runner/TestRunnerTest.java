package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
