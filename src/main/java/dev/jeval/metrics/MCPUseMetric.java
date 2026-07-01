package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.MCPUseSchemas.MCPArgsScore;
import dev.jeval.metrics.MCPUseSchemas.MCPPrimitivesScore;
import java.util.List;
import java.util.Map;

public class MCPUseMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private String availablePrimitives;
    private String primitivesUsed;
    private MCPPrimitivesScore primitivesScore;
    private MCPArgsScore argsScore;

    public MCPUseMetric() {
        this(0.5, true, false);
    }

    public MCPUseMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public MCPUseMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public MCPUseMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("MCP Use threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        requireParams(testCase);
        availablePrimitives = formatAvailableMcpServers(testCase.mcpServers());
        primitivesUsed = formatUsedMcpPrimitives(
                testCase.mcpToolsCalled(), testCase.mcpResourcesCalled(), testCase.mcpPromptsCalled());
        primitivesScore = getPrimitivesUsedScore(testCase, availablePrimitives, primitivesUsed);
        argsScore = getArgumentCorrectnessScore(testCase, availablePrimitives, primitivesUsed);
        score = calculateScore();
        reason = includeReason ? "[\n\t" + primitivesScore.reason() + "\n\t" + argsScore.reason() + "\n]\n" : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "MCP Use";
    }

    public double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public boolean success() {
        return success;
    }

    public String availablePrimitives() {
        return availablePrimitives;
    }

    public String primitivesUsed() {
        return primitivesUsed;
    }

    public MCPPrimitivesScore primitivesScore() {
        return primitivesScore;
    }

    public MCPArgsScore argsScore() {
        return argsScore;
    }

    protected MCPPrimitivesScore getPrimitivesUsedScore(
            LlmTestCase testCase,
            String availablePrimitives,
            String primitivesUsed) {
        requireModel();
        return MCPUseSchemas.parsePrimitivesScore(model.generate(
                MCPUsePrompts.getPrimitiveCorrectnessPrompt(testCase, availablePrimitives, primitivesUsed)));
    }

    protected MCPArgsScore getArgumentCorrectnessScore(
            LlmTestCase testCase,
            String availablePrimitives,
            String primitivesUsed) {
        requireModel();
        return MCPUseSchemas.parseArgsScore(model.generate(
                MCPUsePrompts.getArgumentCorrectnessPrompt(testCase, availablePrimitives, primitivesUsed)));
    }

    private double calculateScore() {
        var calculatedScore = Math.min(primitivesScore.score(), argsScore.score());
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    static String formatAvailableMcpServers(List<Map<String, Object>> servers) {
        var text = new StringBuilder("MCP Primitives Available: \n");
        for (var server : servers) {
            text.append("MCP Server ").append(server.get("server_name")).append('\n');
            appendBlock(text, "Available Tools", listValue(server, "available_tools"));
            appendBlock(text, "Available Resources", listValue(server, "available_resources"));
            appendBlock(text, "Available Prompts", listValue(server, "available_prompts"));
        }
        return text.toString();
    }

    static String formatAvailableMcpTools(List<Map<String, Object>> servers) {
        return formatAvailableMcpBlock(servers, "available_tools", "Available Tools");
    }

    static String formatAvailableMcpInputSchemas(List<Map<String, Object>> servers) {
        return String.join("\n\n",
                formatAvailableMcpBlock(servers, "available_tools", "Available Tools"),
                formatAvailableMcpBlock(servers, "available_resources", "Available Resources"),
                formatAvailableMcpBlock(servers, "available_prompts", "Available Prompts"));
    }

    private static String formatAvailableMcpBlock(List<Map<String, Object>> servers, String key, String title) {
        var text = new StringBuilder();
        for (var server : servers) {
            text.append("MCP Server ").append(server.get("server_name")).append('\n');
            appendBlock(text, title, listValue(server, key));
        }
        return text.toString();
    }

    private static String formatUsedMcpPrimitives(
            List<Map<String, Object>> tools,
            List<Map<String, Object>> resources,
            List<Map<String, Object>> prompts) {
        var text = new StringBuilder("MCP Primitives Used: \n");
        appendBlock(text, "MCP Tools Called", tools);
        appendBlock(text, "MCP Resources Called", resources);
        appendBlock(text, "MCP Prompts Called", prompts);
        return text.toString();
    }

    private static void appendBlock(StringBuilder text, String title, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        text.append("\n").append(title).append(":\n[\n");
        for (var i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(",\n");
            }
            text.append(indentMultiline(values.get(i)));
        }
        text.append("\n]");
    }

    private static String indentMultiline(Object value) {
        return "    " + String.valueOf(value).replace("\n", "\n    ");
    }

    @SuppressWarnings("unchecked")
    private static List<?> listValue(Map<String, Object> map, String key) {
        var value = map.get(key);
        return value instanceof List<?> list ? list : null;
    }

    private void requireParams(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT, SingleTurnParam.MCP_SERVERS),
                name());
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("MCP Use generation requires a model provider");
        }
    }
}
