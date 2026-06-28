package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class ContextualPrecisionSchemas {
    private ContextualPrecisionSchemas() {}

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<ContextualPrecisionVerdict>();
        node.forEach(value -> values.add(new ContextualPrecisionVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static ContextualPrecisionScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ContextualPrecisionScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public static InteractionContextualPrecisionScore parseInteractionScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        var reason = requiredNullableText(node, "reason");
        var verdicts = parseNullableVerdicts(requiredPresent(node, "verdicts"));
        return new InteractionContextualPrecisionScore(MetricUtils.requiredDouble(node, "score"), reason, verdicts);
    }

    private static List<ContextualPrecisionVerdict> parseNullableVerdicts(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a verdict list: verdicts");
        }
        var verdicts = new ArrayList<ContextualPrecisionVerdict>();
        node.forEach(value -> verdicts.add(new ContextualPrecisionVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return List.copyOf(verdicts);
    }

    private static String requiredNullableText(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var value = requiredPresent(node, field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Schema field must be a string: " + field);
        }
        return value.asText();
    }

    private static com.fasterxml.jackson.databind.JsonNode requiredPresent(
            com.fasterxml.jackson.databind.JsonNode node,
            String field) {
        var value = node.path(field);
        if (value.isMissingNode()) {
            throw new IllegalArgumentException("Missing required schema field: " + field);
        }
        return value;
    }

    public record ContextualPrecisionVerdict(String verdict, String reason) {
    }

    public record Verdicts(List<ContextualPrecisionVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record ContextualPrecisionScoreReason(String reason) {
    }

    public record InteractionContextualPrecisionScore(
            double score,
            String reason,
            List<ContextualPrecisionVerdict> verdicts) {
        public InteractionContextualPrecisionScore {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }
}
