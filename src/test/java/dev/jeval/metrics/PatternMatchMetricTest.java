package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Evaluator;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternMatchMetricTest {

    @Test
    void measureSucceedsWhenTrimmedActualOutputFullyMatchesPattern() {
        var metric = new PatternMatchMetric("^[\\w.-]+@[\\w.-]+\\.\\w+$");
        var testCase = new LlmTestCase("email?", "  test@example.com ", null);

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Pattern Match", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The actual output fully matches the pattern.", result.reason()));
    }

    @Test
    void measureFailsWhenOnlyPartOfActualOutputMatchesPattern() {
        var metric = new PatternMatchMetric("\\d+");
        var testCase = new LlmTestCase("number?", "abc123", null);

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals("The actual output does not match the pattern.", result.reason()));
    }

    @Test
    void ignoreCaseCompilesPatternCaseInsensitively() {
        var metric = new PatternMatchMetric("hello", true);

        assertTrue(metric.measure(new LlmTestCase("greeting?", "HELLO", null)).success());
    }

    @Test
    void ignoreCaseUsesUnicodeCaseFoldingLikeDeepEval() {
        var metric = new PatternMatchMetric("éclair", true);

        assertTrue(metric.measure(new LlmTestCase("dessert?", "ÉCLAIR", null)).success());
    }

    @Test
    void evaluateCanRunPatternMatchMetric() {
        var result = Evaluator.evaluate(
                new LlmTestCase("email?", "test@example.com", null),
                List.of(new PatternMatchMetric("^[\\w.-]+@[\\w.-]+\\.\\w+$")));

        assertTrue(result.success());
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var result = new PatternMatchMetric("output")
                .measure(new LlmTestCase("", "output", null));

        assertTrue(result.success());
    }

    @Test
    void measureRequiresActualOutput() {
        assertThrows(MissingTestCaseParamsException.class,
                () -> new PatternMatchMetric(".*").measure(new LlmTestCase("email?", "", null)));
    }

    @Test
    void constructorRejectsInvalidRegexPattern() {
        assertThrows(IllegalArgumentException.class, () -> new PatternMatchMetric("["));
    }

    @Test
    void constructorRejectsMissingPattern() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new PatternMatchMetric(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new PatternMatchMetric(" ")));
    }
}
