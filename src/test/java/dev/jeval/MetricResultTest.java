package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MetricResultTest {

    @Test
    void rejectsNonFiniteScoreAndThreshold() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MetricResult("metric", Double.NaN, 0.5, false, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MetricResult("metric", Double.POSITIVE_INFINITY, 0.5, false, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MetricResult("metric", 0.5, Double.NaN, false, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MetricResult("metric", 0.5, Double.POSITIVE_INFINITY, false, null)));
    }
}
