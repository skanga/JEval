package dev.jeval;

import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record LlmApiTestCase(
        String name,
        String input,
        String actualOutput,
        String expectedOutput,
        List<String> context,
        List<String> retrievalContext,
        List<ToolCall> toolsCalled,
        List<ToolCall> expectedTools,
        Double tokenCost,
        Double completionTime,
        Map<String, MllmImage> imagesMapping,
        Boolean success,
        List<Object> metricsData,
        Double runDuration,
        Double evaluationCost,
        Integer order,
        Map<String, Object> metadata,
        String comments,
        List<String> tags,
        Map<String, Object> trace,
        List<Map<String, Object>> mcpServers,
        List<Map<String, Object>> mcpToolsCalled,
        List<Map<String, Object>> mcpResourcesCalled,
        List<Map<String, Object>> mcpPromptsCalled) {

    public LlmApiTestCase {
        context = context == null ? null : List.copyOf(context);
        retrievalContext = retrievalContext == null ? null : List.copyOf(retrievalContext);
        toolsCalled = toolsCalled == null ? null : List.copyOf(toolsCalled);
        expectedTools = expectedTools == null ? null : List.copyOf(expectedTools);
        imagesMapping = imagesMapping == null ? null : Map.copyOf(imagesMapping);
        metricsData = metricsData == null ? null : List.copyOf(metricsData);
        metadata = copyObjectMap(metadata);
        tags = tags == null ? null : List.copyOf(tags);
        trace = copyObjectMap(trace);
        mcpServers = copyMaps(mcpServers);
        mcpToolsCalled = copyMaps(mcpToolsCalled);
        mcpResourcesCalled = copyMaps(mcpResourcesCalled);
        mcpPromptsCalled = copyMaps(mcpPromptsCalled);
    }

    public Map<String, Object> modelDump() {
        return modelDump(false);
    }

    public LlmApiTestCase updateMetricData(MetricData metricData) {
        var updatedMetricsData = appendMetricData(metricsData, metricData);
        var updatedSuccess = success == null ? metricData.success() : success && metricData.success();
        var updatedEvaluationCost = addEvaluationCost(evaluationCost, metricData.evaluationCost());
        return copyWith(updatedSuccess, updatedMetricsData, runDuration, updatedEvaluationCost);
    }

    public LlmApiTestCase updateRunDuration(double runDuration) {
        return copyWith(success, metricsData, runDuration, evaluationCost);
    }

    public LlmApiTestCase updateStatus(boolean success) {
        var updatedSuccess = this.success == null ? success : this.success && success;
        return copyWith(updatedSuccess, metricsData, runDuration, evaluationCost);
    }

    public LlmApiTestCase withOrder(Integer order) {
        return new LlmApiTestCase(
                name,
                input,
                actualOutput,
                expectedOutput,
                context,
                retrievalContext,
                toolsCalled,
                expectedTools,
                tokenCost,
                completionTime,
                imagesMapping,
                success,
                metricsData,
                runDuration,
                evaluationCost,
                order,
                metadata,
                comments,
                tags,
                trace,
                mcpServers,
                mcpToolsCalled,
                mcpResourcesCalled,
                mcpPromptsCalled);
    }

    public Map<String, Object> modelDump(boolean byAlias) {
        var dump = new LinkedHashMap<String, Object>();
        dump.put("name", name);
        dump.put("input", input);
        dump.put(key("actual_output", "actualOutput", byAlias), actualOutput);
        dump.put(key("expected_output", "expectedOutput", byAlias), expectedOutput);
        dump.put("context", context);
        dump.put(key("retrieval_context", "retrievalContext", byAlias), retrievalContext);
        dump.put(key("tools_called", "toolsCalled", byAlias), dumpTools(toolsCalled, byAlias));
        dump.put(key("expected_tools", "expectedTools", byAlias), dumpTools(expectedTools, byAlias));
        dump.put(key("token_cost", "tokenCost", byAlias), tokenCost);
        dump.put(key("completion_time", "completionTime", byAlias), completionTime);
        dump.put(key("images_mapping", "imagesMapping", byAlias), imagesMapping);
        dump.put("success", success);
        dump.put(key("metrics_data", "metricsData", byAlias), metricsData);
        dump.put(key("run_duration", "runDuration", byAlias), runDuration);
        dump.put(key("evaluation_cost", "evaluationCost", byAlias), evaluationCost);
        dump.put("order", order);
        dump.put("metadata", metadata);
        dump.put("comments", comments);
        dump.put("tags", tags);
        dump.put("trace", trace);
        dump.put(key("mcp_servers", "mcpServers", byAlias), mcpServers);
        dump.put(key("mcp_tools_called", "mcpToolsCalled", byAlias), mcpToolsCalled);
        dump.put(key("mcp_resources_called", "mcpResourcesCalled", byAlias), mcpResourcesCalled);
        dump.put(key("mcp_prompts_called", "mcpPromptsCalled", byAlias), mcpPromptsCalled);
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

    private LlmApiTestCase copyWith(
            Boolean success, List<Object> metricsData, Double runDuration, Double evaluationCost) {
        return new LlmApiTestCase(
                name,
                input,
                actualOutput,
                expectedOutput,
                context,
                retrievalContext,
                toolsCalled,
                expectedTools,
                tokenCost,
                completionTime,
                imagesMapping,
                success,
                metricsData,
                runDuration,
                evaluationCost,
                order,
                metadata,
                comments,
                tags,
                trace,
                mcpServers,
                mcpToolsCalled,
                mcpResourcesCalled,
                mcpPromptsCalled);
    }

    private static List<Map<String, Object>> dumpTools(List<ToolCall> tools, boolean byAlias) {
        return tools == null ? null : tools.stream().map(tool -> tool.modelDump(byAlias)).toList();
    }

    private static String key(String snakeCase, String camelCase, boolean byAlias) {
        return byAlias ? camelCase : snakeCase;
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> values) {
        return values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
