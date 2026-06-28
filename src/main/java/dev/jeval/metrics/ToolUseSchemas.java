package dev.jeval.metrics;

public final class ToolUseSchemas {
    private ToolUseSchemas() {
    }

    public static ToolSelectionScore parseToolSelectionScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ToolSelectionScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public static ArgumentCorrectnessScore parseArgumentCorrectnessScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ArgumentCorrectnessScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public static Reason parseReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Reason(MetricUtils.requiredText(node, "reason"));
    }

    public record UserInputAndTools(
            String userMessages,
            String assistantMessages,
            String toolsCalled,
            String availableTools,
            boolean toolsUsed) {
    }

    public record ToolSelectionScore(double score, String reason) {
    }

    public record ArgumentCorrectnessScore(double score, String reason) {
    }

    public record Reason(String reason) {
    }
}
