package dev.jeval;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ConversationalApiTestCase(
        String name,
        Boolean success,
        List<Object> metricsData,
        Double runDuration,
        Double evaluationCost,
        List<TurnApi> turns,
        Integer order,
        String scenario,
        String expectedOutcome,
        String userDescription,
        List<String> context,
        String comments,
        Map<String, Object> metadata,
        Map<String, MllmImage> imagesMapping,
        List<String> tags,
        List<Map<String, Object>> mcpServers) {

    public ConversationalApiTestCase {
        metricsData = metricsData == null ? null : List.copyOf(metricsData);
        turns = turns == null ? null : List.copyOf(turns);
        context = context == null ? null : List.copyOf(context);
        metadata = copyObjectMap(metadata);
        imagesMapping = imagesMapping == null ? null : Map.copyOf(imagesMapping);
        tags = tags == null ? null : List.copyOf(tags);
        mcpServers = copyMaps(mcpServers);
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("name", name);
        dump.put("success", success);
        dump.put(key("metrics_data", "metricsData", byAlias), metricsData);
        dump.put(key("run_duration", "runDuration", byAlias), runDuration);
        dump.put(key("evaluation_cost", "evaluationCost", byAlias), evaluationCost);
        dump.put("turns", turns == null ? null : turns.stream().map(turn -> turn.modelDump(byAlias)).toList());
        dump.put("order", order);
        dump.put("scenario", scenario);
        dump.put(key("expected_outcome", "expectedOutcome", byAlias), expectedOutcome);
        dump.put(key("user_description", "userDescription", byAlias), userDescription);
        dump.put("context", context);
        dump.put("comments", comments);
        dump.put("metadata", metadata);
        dump.put(key("images_mapping", "imagesMapping", byAlias), imagesMapping);
        dump.put("tags", tags);
        dump.put(key("mcp_servers", "mcpServers", byAlias), mcpServers);
        return dump;
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> values) {
        return values == null
                ? null
                : values.stream().map(value -> Collections.unmodifiableMap(new LinkedHashMap<>(value))).toList();
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
