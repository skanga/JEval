package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArgumentCorrectnessSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no", "idk");

    private ArgumentCorrectnessSchemas() {
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<ArgumentCorrectnessVerdict>();
        node.forEach(value -> values.add(new ArgumentCorrectnessVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static ArgumentCorrectnessScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ArgumentCorrectnessScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record ArgumentCorrectnessVerdict(String verdict, String reason) {
        public ArgumentCorrectnessVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("Invalid verdict: " + verdict);
            }
        }
    }

    public record Verdicts(List<ArgumentCorrectnessVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record ArgumentCorrectnessScoreReason(String reason) {
    }
}
