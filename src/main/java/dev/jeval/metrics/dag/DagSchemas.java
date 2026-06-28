package dev.jeval.metrics.dag;

import com.fasterxml.jackson.databind.JsonNode;
import dev.jeval.metrics.MetricUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DagSchemas {
    private DagSchemas() {
    }

    public static MetricScoreReason parseMetricScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new MetricScoreReason(requiredText(node, "reason"));
    }

    public static TaskNodeOutput parseTaskNodeOutput(String modelOutput) {
        var node = required(MetricUtils.trimAndLoadJson(modelOutput), "output");
        Object output;
        if (node.isArray()) {
            var values = new ArrayList<String>();
            for (var item : node) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("DAG task output list values must be strings");
                }
                values.add(item.asText());
            }
            output = List.copyOf(values);
        } else if (node.isObject()) {
            var values = new LinkedHashMap<String, String>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (!entry.getValue().isTextual()) {
                    throw new IllegalArgumentException("DAG task output map values must be strings");
                }
                values.put(entry.getKey(), entry.getValue().asText());
            }
            output = Map.copyOf(values);
        } else if (node.isTextual()) {
            output = node.asText();
        } else {
            throw new IllegalArgumentException("DAG task output must be a string, string list, or string map");
        }
        return new TaskNodeOutput(output);
    }

    public static BinaryJudgementVerdict parseBinaryJudgementVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new BinaryJudgementVerdict(requiredBoolean(node, "verdict"), requiredText(node, "reason"));
    }

    public static NonBinaryJudgementVerdict parseNonBinaryJudgementVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new NonBinaryJudgementVerdict(requiredText(node, "verdict"), requiredText(node, "reason"));
    }

    private static JsonNode required(JsonNode node, String field) {
        var value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing required DAG schema field: " + field);
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        var value = required(node, field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("DAG schema field must be a string: " + field);
        }
        return value.asText();
    }

    private static boolean requiredBoolean(JsonNode node, String field) {
        var value = required(node, field);
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isInt()) {
            if (value.asInt() == 1) {
                return true;
            }
            if (value.asInt() == 0) {
                return false;
            }
        }
        if (value.isTextual()) {
            return switch (value.asText().toLowerCase()) {
                case "true", "yes", "1" -> true;
                case "false", "no", "0" -> false;
                default -> throw new IllegalArgumentException("DAG schema field must be a boolean: " + field);
            };
        }
        throw new IllegalArgumentException("DAG schema field must be a boolean: " + field);
    }

    public record MetricScoreReason(String reason) {
    }

    public record TaskNodeOutput(Object output) {
    }

    public record BinaryJudgementVerdict(boolean verdict, String reason) {
    }

    public record NonBinaryJudgementVerdict(String verdict, String reason) {
    }
}
