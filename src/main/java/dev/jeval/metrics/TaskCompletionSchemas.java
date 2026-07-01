package dev.jeval.metrics;

public final class TaskCompletionSchemas {
    private TaskCompletionSchemas() {
    }

    public static TaskAndOutcome parseTaskAndOutcome(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new TaskAndOutcome(MetricUtils.requiredText(node, "task"), MetricUtils.requiredText(node, "outcome"));
    }

    public static TaskCompletionVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        var score = node.has("score")
                ? MetricUtils.requiredDouble(node, "score")
                : MetricUtils.requiredDouble(node, "verdict");
        return new TaskCompletionVerdict(score, MetricUtils.optionalText(node, "reason"));
    }

    public static TaskCompletionVerdict parseTaskScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new TaskCompletionVerdict(
                MetricUtils.requiredDouble(node, "score"),
                MetricUtils.requiredText(node, "reason"));
    }

    public record TaskAndOutcome(String task, String outcome) {
    }

    public record TaskCompletionVerdict(double verdict, String reason) {
        public TaskCompletionVerdict {
            if (!Double.isFinite(verdict)) {
                throw new IllegalArgumentException("TaskCompletionVerdict verdict must be finite");
            }
        }
    }
}
