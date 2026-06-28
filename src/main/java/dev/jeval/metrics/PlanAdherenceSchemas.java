package dev.jeval.metrics;

public final class PlanAdherenceSchemas {
    private PlanAdherenceSchemas() {
    }

    public static PlanAdherenceScore parseScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new PlanAdherenceScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public record PlanAdherenceScore(double score, String reason) {
    }
}
