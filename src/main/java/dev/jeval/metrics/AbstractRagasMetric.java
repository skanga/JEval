package dev.jeval.metrics;

import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

abstract class AbstractRagasMetric implements Metric {
    private final double threshold;
    private final RagasScorer scorer;
    private final List<SingleTurnParam> requiredParams;
    private double score;
    private boolean success;

    AbstractRagasMetric(double threshold, RagasScorer scorer, List<SingleTurnParam> requiredParams) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("RAGAS threshold must be finite");
        }
        this.threshold = threshold;
        this.scorer = Objects.requireNonNull(scorer, "scorer");
        this.requiredParams = List.copyOf(requiredParams);
    }

    @Override
    public final MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(testCase, requiredParams, name());
        if (requiredParams.contains(SingleTurnParam.RETRIEVAL_CONTEXT) && testCase.retrievalContext() == null) {
            throw new MissingTestCaseParamsException(
                    "'retrieval_context' cannot be None for the '" + name() + "' metric");
        }
        score = scorer.score(metricKey(), testCase);
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, null);
    }

    public final CompletableFuture<MetricResult> aMeasure(LlmTestCase testCase) {
        return CompletableFuture.supplyAsync(() -> measure(testCase));
    }

    public abstract String metricKey();

    public abstract String baseName();

    public final String name() {
        return formatRagasMetricName(baseName());
    }

    public final double score() {
        return score;
    }

    public final double threshold() {
        return threshold;
    }

    public final boolean success() {
        return success;
    }

    static RagasScorer unavailableScorer() {
        return (metricKey, testCase) -> {
            throw new UnsupportedOperationException(
                    "RAGAS scoring requires an external RAGAS-compatible scorer.");
        };
    }

    static String formatRagasMetricName(String name) {
        return name + " (ragas)";
    }
}
