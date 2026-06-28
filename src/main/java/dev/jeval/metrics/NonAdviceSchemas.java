package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class NonAdviceSchemas {
    private NonAdviceSchemas() {
    }

    public static Advices parseAdvices(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Advices(MetricUtils.requiredStringList(node, "advices"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<NonAdviceVerdict>();
        node.forEach(value -> values.add(new NonAdviceVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static NonAdviceScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new NonAdviceScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record Advices(List<String> advices) {
        public Advices {
            advices = advices == null ? null : List.copyOf(advices);
        }
    }

    public record NonAdviceVerdict(String verdict, String reason) {
    }

    public record Verdicts(List<NonAdviceVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record NonAdviceScoreReason(String reason) {
    }
}
