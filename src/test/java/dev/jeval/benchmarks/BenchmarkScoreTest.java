package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BenchmarkScoreTest {

    @Test
    void rejectsNonFiniteScores() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BenchmarkTaskScore("task", Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BenchmarkTaskScore("task", Double.POSITIVE_INFINITY)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BenchmarkResult(Double.NaN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new BenchmarkResult(Double.POSITIVE_INFINITY)));
    }
}
