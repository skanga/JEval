package dev.jeval.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KnowledgeRetentionSchemas {
    private KnowledgeRetentionSchemas() {
    }

    public static Knowledge parseKnowledge(String modelOutput) {
        var root = MetricUtils.trimAndLoadJson(modelOutput);
        rejectExtraFields(root, Set.of("data"));
        var data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return new Knowledge(null);
        }
        if (!data.isObject()) {
            throw new IllegalArgumentException("Schema field must be an object: data");
        }
        var values = new LinkedHashMap<String, Object>();
        data.fields().forEachRemaining(entry -> values.put(entry.getKey(), value(entry.getValue())));
        return new Knowledge(values);
    }

    public static KnowledgeRetentionVerdict parseVerdict(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        rejectExtraFields(node, Set.of("verdict", "reason"));
        return new KnowledgeRetentionVerdict(
                MetricUtils.requiredText(node, "verdict"),
                MetricUtils.optionalText(node, "reason"));
    }

    public static KnowledgeRetentionScoreReason parseReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        rejectExtraFields(node, Set.of("reason"));
        return new KnowledgeRetentionScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    private static Object value(JsonNode node) {
        if (node.isArray()) {
            var values = new ArrayList<String>();
            node.forEach(item -> {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("Schema list values must be strings: data");
                }
                values.add(item.asText());
            });
            return List.copyOf(values);
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException("Schema field values must be strings or string lists: data");
        }
        return node.asText();
    }

    private static void rejectExtraFields(JsonNode node, Set<String> allowedFields) {
        node.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException("Unexpected schema field: " + field);
            }
        });
    }

    public record Knowledge(Map<String, Object> data) {
        public Knowledge {
            data = data == null ? null : Map.copyOf(data);
        }
    }

    public record KnowledgeRetentionVerdict(String verdict, String reason) {
    }

    public record KnowledgeRetentionScoreReason(String reason) {
    }
}
