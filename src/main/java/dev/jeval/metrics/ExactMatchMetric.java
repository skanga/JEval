package dev.jeval.metrics;

import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import java.util.List;

public final class ExactMatchMetric implements Metric {
    private final double threshold;
    private double score;
    private double precision;
    private double recall;
    private double f1;
    private String reason;
    private boolean success;

    public ExactMatchMetric() {
        this(1.0);
    }

    public ExactMatchMetric(double threshold) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Exact Match threshold must be finite");
        }
        this.threshold = threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT, SingleTurnParam.EXPECTED_OUTPUT),
                name());

        if (testCase.expectedOutput().strip().equals(testCase.actualOutput().strip())) {
            score = precision = recall = f1 = 1.0;
            reason = "The actual and expected outputs are exact matches.";
        } else {
            score = precision = recall = f1 = 0.0;
            reason = "The actual and expected outputs are different.";
        }
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Exact Match";
    }

    public double score() {
        return score;
    }

    public double precision() {
        return precision;
    }

    public double recall() {
        return recall;
    }

    public double f1() {
        return f1;
    }

    public String reason() {
        return reason;
    }

    public boolean success() {
        return success;
    }
}
