package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BiasSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no");

    private BiasSchemas() {}

    public static Opinions parseOpinions(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Opinions(MetricUtils.requiredStringList(node, "opinions"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<BiasVerdict>();
        node.forEach(value -> values.add(new BiasVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static BiasScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new BiasScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record Opinions(List<String> opinions) {
        public Opinions {
            opinions = opinions == null ? null : List.copyOf(opinions);
        }
    }

    public record BiasVerdict(String verdict, String reason) {
        public BiasVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("verdict must be one of: yes, no");
            }
        }
    }

    public record Verdicts(List<BiasVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record BiasScoreReason(String reason) {
    }
}
