package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.metrics.MetricUtils;
import org.junit.jupiter.api.Test;

class MetricDataTest {

    @Test
    void fromEvaluationCostAccruesCostAndTokenCountsLikeDeepEval() {
        var cost = new EvaluationCost(0.125, 12, 34);

        var data = MetricUtils.metricDataWithEvaluationCost(
                "faithfulness",
                0.5,
                true,
                0.75,
                "ok",
                false,
                "gpt",
                null,
                cost,
                "logs");

        assertEquals(0.125, data.evaluationCost());
        assertEquals(12, data.inputTokenCount());
        assertEquals(34, data.outputTokenCount());
    }

    @Test
    void rejectsInvalidNumericValues() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(Double.NaN, 0.5, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(0.5, Double.POSITIVE_INFINITY, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(0.5, null, -0.01, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(0.5, null, Double.NaN, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(0.5, null, null, -1, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> metricData(0.5, null, null, null, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationCost(Double.NaN, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationCost(-0.01, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationCost(0.01, -1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EvaluationCost(0.01, 0, -1)));
    }

    private static MetricData metricData(
            double threshold, Double score, Double evaluationCost, Integer inputTokens, Integer outputTokens) {
        return new MetricData(
                "faithfulness",
                threshold,
                true,
                score,
                null,
                false,
                "gpt",
                null,
                evaluationCost,
                inputTokens,
                outputTokens,
                null);
    }
}
