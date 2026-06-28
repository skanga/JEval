package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class RoleAdherenceSchemas {
    private RoleAdherenceSchemas() {
    }

    public static OutOfCharacterResponseVerdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var verdicts = new ArrayList<OutOfCharacterResponseVerdict>();
        node.forEach(value -> verdicts.add(parseVerdict(value)));
        return new OutOfCharacterResponseVerdicts(verdicts);
    }

    public static RoleAdherenceScoreReason parseReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new RoleAdherenceScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    private static OutOfCharacterResponseVerdict parseVerdict(com.fasterxml.jackson.databind.JsonNode node) {
        return new OutOfCharacterResponseVerdict(
                requiredInt(node, "index"),
                MetricUtils.requiredText(node, "reason"),
                MetricUtils.optionalText(node, "ai_message"));
    }

    private static int requiredInt(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = MetricUtils.required(node, field);
        if (!value.isInt()) {
            throw new IllegalArgumentException("Schema field must be an integer: " + field);
        }
        return value.asInt();
    }

    public record OutOfCharacterResponseVerdict(int index, String reason, String aiMessage) {
    }

    public record OutOfCharacterResponseVerdicts(List<OutOfCharacterResponseVerdict> verdicts) {
        public OutOfCharacterResponseVerdicts {
            verdicts = verdicts == null ? List.of() : List.copyOf(verdicts);
        }
    }

    public record RoleAdherenceScoreReason(String reason) {
    }
}
