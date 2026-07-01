package dev.jeval;

import java.util.Collections;
import java.util.ArrayList;
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
        if (runDuration != null && (!Double.isFinite(runDuration) || runDuration < 0.0)) {
            throw new IllegalArgumentException("ConversationalApiTestCase runDuration must be finite and non-negative");
        }
        if (evaluationCost != null && (!Double.isFinite(evaluationCost) || evaluationCost < 0.0)) {
            throw new IllegalArgumentException("ConversationalApiTestCase evaluationCost must be finite and non-negative");
        }
        if (order != null && order < 0) {
            throw new IllegalArgumentException("ConversationalApiTestCase order must be non-negative");
        }
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public ConversationalApiTestCase updateMetricData(MetricData metricData) {
        var updatedMetricsData = appendMetricData(metricsData, metricData);
        var updatedSuccess = metricData.success() ? success : false;
        var updatedEvaluationCost = addEvaluationCost(evaluationCost, metricData.evaluationCost());
        return copyWith(updatedSuccess, updatedMetricsData, runDuration, updatedEvaluationCost);
    }

    public ConversationalApiTestCase updateRunDuration(double runDuration) {
        var updatedRunDuration = (this.runDuration == null ? 0.0 : this.runDuration) + runDuration;
        return copyWith(success, metricsData, updatedRunDuration, evaluationCost);
    }

    public ConversationalApiTestCase withOrder(Integer order) {
        return new ConversationalApiTestCase(
                name,
                success,
                metricsData,
                runDuration,
                evaluationCost,
                turns,
                order,
                scenario,
                expectedOutcome,
                userDescription,
                context,
                comments,
                metadata,
                imagesMapping,
                tags,
                mcpServers);
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

    private static List<Object> appendMetricData(List<Object> metricsData, MetricData metricData) {
        var updated = new ArrayList<Object>();
        if (metricsData != null) {
            updated.addAll(metricsData);
        }
        updated.add(metricData);
        return updated;
    }

    private static Double addEvaluationCost(Double existing, Double additional) {
        if (additional == null) {
            return existing;
        }
        return existing == null ? additional : existing + additional;
    }

    private ConversationalApiTestCase copyWith(
            Boolean success, List<Object> metricsData, Double runDuration, Double evaluationCost) {
        return new ConversationalApiTestCase(
                name,
                success,
                metricsData,
                runDuration,
                evaluationCost,
                turns,
                order,
                scenario,
                expectedOutcome,
                userDescription,
                context,
                comments,
                metadata,
                imagesMapping,
                tags,
                mcpServers);
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
