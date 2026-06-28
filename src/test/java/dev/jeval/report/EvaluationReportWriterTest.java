package dev.jeval.report;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.runner.TestRunner;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesMarkdownAndHtmlReportsWithAggregateMetrics() throws Exception {
        var file = tempDir.resolve("eval.json");
        Files.writeString(file, """
                {
                  "name": "report-demo",
                  "metrics": [{"type": "exact_match"}],
                  "cases": [
                    {"name": "one", "input": "q1", "actualOutput": "a", "expectedOutput": "a"},
                    {"name": "two", "input": "q2", "actualOutput": "b", "expectedOutput": "c"}
                  ]
                }
                """);
        var result = new TestRunner().run(file);

        var markdown = EvaluationReportWriter.markdown(result);
        var html = EvaluationReportWriter.html(result);

        assertTrue(markdown.contains("JEval Evaluation Results"));
        assertTrue(markdown.contains("Aggregate Metrics"));
        assertTrue(markdown.contains("Exact Match"));
        assertTrue(markdown.contains("0.50"));
        assertTrue(markdown.contains("50.00%"));
        assertTrue(html.contains("JEval Evaluation Results"));
        assertTrue(html.contains("Aggregate Metrics"));
        assertTrue(html.contains("Exact Match"));
    }
}
