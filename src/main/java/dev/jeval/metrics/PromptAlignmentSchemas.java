package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class PromptAlignmentSchemas {
    private PromptAlignmentSchemas() {
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<PromptAlignmentVerdict>();
        node.forEach(value -> values.add(new PromptAlignmentVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static PromptAlignmentScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new PromptAlignmentScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record PromptAlignmentVerdict(String verdict, String reason) {
    }

    public record Verdicts(List<PromptAlignmentVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record PromptAlignmentScoreReason(String reason) {
    }
}
