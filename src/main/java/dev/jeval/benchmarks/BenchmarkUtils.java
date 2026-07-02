package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;

public final class BenchmarkUtils {

    private BenchmarkUtils() {
    }

    public static boolean shouldUseBatch(EvaluationModel model, Integer batchSize) {
        return model != null && batchSize != null;
    }
}
