package dev.jeval.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FaithfulnessSchemas {
    private static final Set<String> VALID_VERDICTS = Set.of("yes", "no", "idk");

    private FaithfulnessSchemas() {}

    public static Truths parseTruths(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Truths(MetricUtils.requiredStringList(node, "truths"));
    }

    public static Claims parseClaims(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new Claims(MetricUtils.requiredStringList(node, "claims"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        return new Verdicts(parseVerdictList(node));
    }

    public static FaithfulnessScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new FaithfulnessScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public static TurnFaithfulnessMetric.InteractionFaithfulnessScore parseInteractionScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new TurnFaithfulnessMetric.InteractionFaithfulnessScore(
                MetricUtils.requiredDouble(node, "score"),
                requiredNullableText(node, "reason"),
                MetricUtils.requiredStringList(node, "claims"),
                MetricUtils.requiredStringList(node, "truths"),
                parseVerdictList(MetricUtils.required(node, "verdicts")));
    }

    private static List<FaithfulnessVerdict> parseVerdictList(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<FaithfulnessVerdict>();
        node.forEach(value -> values.add(new FaithfulnessVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.optionalText(value, "reason"))));
        return List.copyOf(values);
    }

    private static String requiredNullableText(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode()) {
            throw new IllegalArgumentException("Missing required schema field: " + field);
        }
        if (value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Schema field must be a string: " + field);
        }
        return value.asText();
    }

    public record FaithfulnessVerdict(String verdict, String reason) {
        public FaithfulnessVerdict {
            if (!VALID_VERDICTS.contains(verdict)) {
                throw new IllegalArgumentException("verdict must be one of: yes, no, idk");
            }
        }
    }

    public record Verdicts(List<FaithfulnessVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record Truths(List<String> truths) {
        public Truths {
            truths = truths == null ? null : List.copyOf(truths);
        }
    }

    public record Claims(List<String> claims) {
        public Claims {
            claims = claims == null ? null : List.copyOf(claims);
        }
    }

    public record FaithfulnessScoreReason(String reason) {}
}
