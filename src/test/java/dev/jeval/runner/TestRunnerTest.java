package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
