package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class HallucinationSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no");

    private HallucinationSchemas() {}

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<HallucinationVerdict>();
        node.forEach(value -> values.add(new HallucinationVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static HallucinationScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new HallucinationScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record HallucinationVerdict(String verdict, String reason) {
        public HallucinationVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("verdict must be one of: yes, no");
            }
        }
    }

    public record Verdicts(List<HallucinationVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record HallucinationScoreReason(String reason) {
    }
}
