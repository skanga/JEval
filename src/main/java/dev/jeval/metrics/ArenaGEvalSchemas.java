package dev.jeval.metrics;

import java.util.List;

final class ArenaGEvalSchemas {
    private ArenaGEvalSchemas() {
    }

    static Steps parseSteps(String modelOutput) {
        return new Steps(MetricUtils.requiredStringList(
                MetricUtils.trimAndLoadJson(modelOutput),
                "steps"));
    }

    static ReasonScore parseReasonScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ReasonScore(
                MetricUtils.requiredText(node, "reason"),
                MetricUtils.requiredDouble(node, "score"));
    }

    static Winner parseWinner(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Winner(
                MetricUtils.requiredText(node, "winner"),
                MetricUtils.requiredText(node, "reason"));
    }

    static RewrittenReason parseRewrittenReason(String modelOutput) {
        return new RewrittenReason(MetricUtils.requiredText(
                MetricUtils.trimAndLoadJson(modelOutput),
                "rewritten_reason"));
    }

    record Steps(List<String> steps) {
        Steps {
            steps = steps == null ? null : List.copyOf(steps);
        }
    }

    record ReasonScore(String reason, double score) {
        ReasonScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("ArenaGEval ReasonScore score must be finite");
            }
        }
    }

    record Winner(String winner, String reason) {
    }

    record RewrittenReason(String rewrittenReason) {
    }
}
