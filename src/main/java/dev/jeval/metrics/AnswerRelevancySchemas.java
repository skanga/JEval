package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class AnswerRelevancySchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no", "idk");

    private AnswerRelevancySchemas() {}

    public static Statements parseStatements(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Statements(MetricUtils.requiredStringList(node, "statements"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<AnswerRelevancyVerdict>();
        node.forEach(value -> values.add(new AnswerRelevancyVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new Verdicts(values);
    }

    public static AnswerRelevancyScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new AnswerRelevancyScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record Statements(List<String> statements) {
        public Statements {
            statements = statements == null ? null : List.copyOf(statements);
        }
    }

    public record AnswerRelevancyVerdict(String verdict, String reason) {
        public AnswerRelevancyVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("verdict must be one of: yes, no, idk");
            }
        }
    }

    public record Verdicts(List<AnswerRelevancyVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record AnswerRelevancyScoreReason(String reason) {}
}
