package dev.jeval.metrics;

final class TurnRelevancySchemas {
    private TurnRelevancySchemas() {
    }

    static TurnRelevancyVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new TurnRelevancyVerdict(
                MetricUtils.requiredText(node, "verdict"),
                MetricUtils.optionalText(node, "reason"));
    }

    static TurnRelevancyReason parseReason(String modelOutput) {
        return new TurnRelevancyReason(MetricUtils.requiredText(MetricUtils.trimAndLoadJson(modelOutput), "reason"));
    }

    record TurnRelevancyVerdict(String verdict, String reason) {
    }

    record TurnRelevancyReason(String reason) {
    }
}
