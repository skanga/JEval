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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExactMatchMetricTest {

    @Test
    void measureSucceedsWhenTrimmedActualAndExpectedMatch() {
        var testCase = new LlmTestCase(
                "What if these shoes don't fit?",
                "  We offer a 30-day full refund at no extra cost. ",
                "We offer a 30-day full refund at no extra cost.");
        var metric = new ExactMatchMetric();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Exact Match", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("The actual and expected outputs are exact matches.", result.reason()),
                () -> assertEquals(1.0, metric.score()),
                () -> assertEquals(1.0, metric.precision()),
                () -> assertEquals(1.0, metric.recall()),
                () -> assertEquals(1.0, metric.f1()),
                () -> assertTrue(metric.success()));
    }

    @Test
    void measureFailsWhenActualAndExpectedDiffer() {
        var testCase = new LlmTestCase("What is 2+2?", "5", "4");
        var metric = new ExactMatchMetric();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(0.0, metric.precision()),
                () -> assertEquals(0.0, metric.recall()),
                () -> assertEquals(0.0, metric.f1()),
                () -> assertEquals("The actual and expected outputs are different.", result.reason()));
    }

    @Test
    void evaluateCanRunExactMatchMetric() {
        var testCase = new LlmTestCase("What is 2+2?", "4", "4");

        var result = Evaluator.evaluate(testCase, List.of(new ExactMatchMetric()));

        assertTrue(result.success());
    }

    @Test
    void asyncMeasureMatchesSynchronousMetricBehaviorLikeDeepEval() throws Exception {
        var metric = new ExactMatchMetric();

        var result = metric.aMeasure(new LlmTestCase("What is 2+2?", "4", "4"))
                .get(5, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals("Exact Match", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    @Test
    void measureAllowsEmptyInputAndExpectedOutputLikeDeepEval() {
        var metric = new ExactMatchMetric();

        var result = metric.measure(new LlmTestCase("", " ", ""));

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    @Test
    void measureRequiresActualOutput() {
        var testCase = new LlmTestCase("What is 2+2?", "", "4");

        assertThrows(MissingTestCaseParamsException.class,
                () -> new ExactMatchMetric().measure(testCase));
    }
}
