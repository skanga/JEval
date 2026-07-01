package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MetricDataTest {

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
                        () -> metricData(0.5, null, null, null, -1)));
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
