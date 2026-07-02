package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import org.junit.jupiter.api.Test;

class BenchmarkUtilsTest {

    @Test
    void shouldUseBatchRequiresBatchSizeLikeDeepEval() {
        EvaluationModel model = prompt -> prompt;

        assertFalse(BenchmarkUtils.shouldUseBatch(model, null));
        assertTrue(BenchmarkUtils.shouldUseBatch(model, 2));
    }

    @Test
    void shouldUseBatchReturnsFalseWithoutModel() {
        assertFalse(BenchmarkUtils.shouldUseBatch(null, 2));
    }
}
