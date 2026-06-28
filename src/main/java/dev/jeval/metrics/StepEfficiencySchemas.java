package dev.jeval.metrics;

public final class StepEfficiencySchemas {
    private StepEfficiencySchemas() {
    }

    public static Task parseTask(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Task(MetricUtils.requiredText(node, "task"));
    }

    public static EfficiencyVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new EfficiencyVerdict(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public record Task(String task) {
    }

    public record EfficiencyVerdict(double score, String reason) {
    }
}
