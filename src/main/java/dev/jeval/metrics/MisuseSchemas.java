package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MisuseSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no");

    private MisuseSchemas() {
    }

    public static Misuses parseMisuses(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Misuses(MetricUtils.requiredStringList(node, "misuses"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<MisuseVerdict>();
        node.forEach(value -> values.add(new MisuseVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static MisuseScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new MisuseScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record Misuses(List<String> misuses) {
        public Misuses {
            misuses = misuses == null ? null : List.copyOf(misuses);
        }
    }

    public record MisuseVerdict(String verdict, String reason) {
        public MisuseVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("verdict must be one of: yes, no");
            }
        }
    }

    public record Verdicts(List<MisuseVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record MisuseScoreReason(String reason) {
    }
}
