package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class ContextualRecallSchemas {
    private ContextualRecallSchemas() {}

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<ContextualRecallVerdict>();
        node.forEach(value -> values.add(new ContextualRecallVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static ContextualRecallScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ContextualRecallScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public static InteractionContextualRecallScore parseInteractionScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        var reason = requiredNullableText(node, "reason");
        var verdicts = parseNullableVerdicts(requiredPresent(node, "verdicts"));
        return new InteractionContextualRecallScore(MetricUtils.requiredDouble(node, "score"), reason, verdicts);
    }

    private static List<ContextualRecallVerdict> parseNullableVerdicts(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a verdict list: verdicts");
        }
        var verdicts = new ArrayList<ContextualRecallVerdict>();
        node.forEach(value -> verdicts.add(new ContextualRecallVerdict(
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

    public record ContextualRecallVerdict(String verdict, String reason) {
    }

    public record VerdictWithExpectedOutput(String verdict, String reason, String expectedOutput) {
    }

    public record Verdicts(List<ContextualRecallVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record ContextualRecallScoreReason(String reason) {
    }

    public record InteractionContextualRecallScore(
            double score,
            String reason,
            List<ContextualRecallVerdict> verdicts) {
        public InteractionContextualRecallScore {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }
}
