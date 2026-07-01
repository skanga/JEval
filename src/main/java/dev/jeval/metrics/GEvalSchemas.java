package dev.jeval.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

public final class GEvalSchemas {
    private GEvalSchemas() {
    }

    public static Steps parseSteps(String modelOutput) {
        var node = required(MetricUtils.trimAndLoadJson(modelOutput), "steps");
        if (!node.isArray()) {
            throw new IllegalArgumentException("GEval schema field must be a string list: steps");
        }
        var values = new ArrayList<String>();
        for (var value : node) {
            if (!value.isTextual()) {
                throw new IllegalArgumentException("GEval steps values must be strings");
            }
            values.add(value.asText());
        }
        return new Steps(values);
    }

    public static ReasonScore parseReasonScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new ReasonScore(requiredText(node, "reason"), requiredDouble(node, "score"));
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing required GEval schema field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("GEval schema field must be a string: " + field);
        }
        return value.asText();
    }

    private static double requiredDouble(JsonNode node, String field) {
        var value = required(node, field);
        if (value.isNumber()) {
            return finiteDouble(value.asDouble(), field);
        }
        if (value.isTextual()) {
            try {
                return finiteDouble(Double.parseDouble(value.asText()), field);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("GEval schema field must be a number: " + field, error);
            }
        }
        throw new IllegalArgumentException("GEval schema field must be a number: " + field);
    }

    private static double finiteDouble(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("GEval schema field must be a finite number: " + field);
        }
        return value;
    }

    public record Steps(List<String> steps) {
        public Steps {
            steps = steps == null ? null : List.copyOf(steps);
        }
    }

    public record ReasonScore(String reason, double score) {
        public ReasonScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("GEval ReasonScore score must be finite");
            }
        }
    }
}
