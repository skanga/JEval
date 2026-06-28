package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class ContextualRelevancySchemas {
    private ContextualRelevancySchemas() {}

    public static ContextualRelevancyVerdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<ContextualRelevancyVerdict>();
        node.forEach(value -> values.add(new ContextualRelevancyVerdict(
                MetricUtils.requiredText(value, "statement"),
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return new ContextualRelevancyVerdicts(values);
    }

    public static ContextualRelevancyScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ContextualRelevancyScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public static InteractionContextualRelevancyScore parseInteractionScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        var reason = requiredNullableText(node, "reason");
        var verdicts = parseNullableVerdicts(requiredPresent(node, "verdicts"));
        return new InteractionContextualRelevancyScore(MetricUtils.requiredDouble(node, "score"), reason, verdicts);
    }

    private static List<ContextualRelevancyVerdict> parseNullableVerdicts(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a verdict list: verdicts");
        }
        var verdicts = new ArrayList<ContextualRelevancyVerdict>();
        node.forEach(value -> verdicts.add(new ContextualRelevancyVerdict(
                MetricUtils.requiredText(value, "statement"),
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
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

    public record ContextualRelevancyVerdict(String statement, String verdict, String reason) {
    }

    public record ContextualRelevancyVerdicts(List<ContextualRelevancyVerdict> verdicts) {
        public ContextualRelevancyVerdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record ContextualRelevancyScoreReason(String reason) {
    }

    public record InteractionContextualRelevancyScore(
            double score,
            String reason,
            List<ContextualRelevancyVerdict> verdicts) {
        public InteractionContextualRelevancyScore {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }
}
