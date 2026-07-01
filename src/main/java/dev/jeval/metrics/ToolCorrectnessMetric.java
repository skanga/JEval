package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.ToolCall;
import dev.jeval.ToolCallParam;
import dev.jeval.metrics.ToolUseSchemas.ToolSelectionScore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class ToolCorrectnessMetric implements Metric {
    private final List<ToolCallParam> evaluationParams;
    private final boolean shouldExactMatch;
    private final boolean shouldConsiderOrdering;
    private final boolean strictMode;
    private final double threshold;
    private final List<ToolCall> availableTools;
    private final EvaluationModel model;
    private double score;
    private String reason;
    private boolean success;

    public ToolCorrectnessMetric() {
        this(List.of(), false, false, 0.5);
    }

    public ToolCorrectnessMetric(List<ToolCallParam> evaluationParams) {
        this(evaluationParams, false, false, 0.5);
    }

    public ToolCorrectnessMetric(boolean shouldExactMatch, boolean shouldConsiderOrdering) {
        this(List.of(), shouldExactMatch, shouldConsiderOrdering, 0.5);
    }

    public ToolCorrectnessMetric(List<ToolCall> availableTools, EvaluationModel model) {
        this(List.of(), false, false, 0.5, false, availableTools, model);
    }

    public ToolCorrectnessMetric(
            List<ToolCallParam> evaluationParams,
            boolean shouldExactMatch,
            boolean shouldConsiderOrdering,
            double threshold) {
        this(evaluationParams, shouldExactMatch, shouldConsiderOrdering, threshold, false);
    }

    public ToolCorrectnessMetric(
            List<ToolCallParam> evaluationParams,
            boolean shouldExactMatch,
            boolean shouldConsiderOrdering,
            double threshold,
            boolean strictMode) {
        this(evaluationParams, shouldExactMatch, shouldConsiderOrdering, threshold, strictMode, null, null);
    }

    private ToolCorrectnessMetric(
            List<ToolCallParam> evaluationParams,
            boolean shouldExactMatch,
            boolean shouldConsiderOrdering,
            double threshold,
            boolean strictMode,
            List<ToolCall> availableTools,
            EvaluationModel model) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Tool Correctness threshold must be finite");
        }
        this.evaluationParams = List.copyOf(evaluationParams);
        this.shouldExactMatch = shouldExactMatch;
        this.shouldConsiderOrdering = shouldConsiderOrdering;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        this.model = model;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        requireTools(testCase);
        var toolCallingScore = calculateScore(testCase.toolsCalled(), testCase.expectedTools());
        var toolSelectionScore = getToolSelectionScore(testCase);
        score = Math.min(toolCallingScore, toolSelectionScore.score());
        if (strictMode && score < threshold) {
            score = 0.0;
        }
        success = score >= threshold;
        reason = finalReason(
                toolCallingReason(testCase.toolsCalled(), testCase.expectedTools(), toolCallingScore),
                toolSelectionScore.reason());
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Tool Correctness";
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

    private double calculateScore(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        if (shouldExactMatch) {
            return exactScore(toolsCalled, expectedTools);
        }
        if (shouldConsiderOrdering) {
            return orderingScore(toolsCalled, expectedTools);
        }
        return nonExactScore(toolsCalled, expectedTools);
    }

    private double exactScore(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        if (toolsCalled.size() != expectedTools.size()) {
            return 0.0;
        }
        if (toolsCalled.isEmpty()) {
            return 1.0;
        }
        for (var i = 0; i < toolsCalled.size(); i++) {
            if (toolMatchScore(toolsCalled.get(i), expectedTools.get(i)) != 1.0) {
                return 0.0;
            }
        }
        return 1.0;
    }

    private double nonExactScore(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        if (expectedTools.isEmpty()) {
            return toolsCalled.isEmpty() ? 1.0 : 0.0;
        }

        var totalScore = 0.0;
        var matchedCalledTools = new HashSet<Integer>();
        for (var expectedTool : expectedTools) {
            var bestScore = 0.0;
            var bestIndex = -1;
            for (var i = 0; i < toolsCalled.size(); i++) {
                if (matchedCalledTools.contains(i)) {
                    continue;
                }
                var matchScore = toolMatchScore(toolsCalled.get(i), expectedTool);
                if (matchScore > bestScore) {
                    bestScore = matchScore;
                    bestIndex = i;
                }
            }
            if (bestScore > 0.0) {
                totalScore += bestScore;
                matchedCalledTools.add(bestIndex);
            }
        }
        return totalScore / expectedTools.size();
    }

    private double orderingScore(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        if (expectedTools.isEmpty()) {
            return toolsCalled.isEmpty() ? 1.0 : 0.0;
        }

        var expectedCount = expectedTools.size();
        var calledCount = toolsCalled.size();
        var dp = new double[expectedCount + 1][calledCount + 1];
        for (var i = 1; i <= expectedCount; i++) {
            for (var j = 1; j <= calledCount; j++) {
                var matchScore = toolMatchScore(toolsCalled.get(j - 1), expectedTools.get(i - 1));
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                if (matchScore > 0.0) {
                    dp[i][j] = Math.max(dp[i][j], dp[i - 1][j - 1] + matchScore);
                }
            }
        }
        return dp[expectedCount][calledCount] / expectedCount;
    }

    private double toolMatchScore(ToolCall calledTool, ToolCall expectedTool) {
        if (!calledTool.name().equals(expectedTool.name())) {
            return 0.0;
        }
        var score = 1.0;
        if (evaluationParams.contains(ToolCallParam.INPUT_PARAMETERS)) {
            score *= compareMaps(expectedTool.inputParameters(), calledTool.inputParameters());
        }
        if (evaluationParams.contains(ToolCallParam.OUTPUT) && !java.util.Objects.equals(expectedTool.output(), calledTool.output())) {
            score = 0.0;
        }
        return score;
    }

    private double compareMaps(Map<String, Object> expected, Map<String, Object> actual) {
        if (java.util.Objects.equals(expected, actual)) {
            return 1.0;
        }
        if (expected == null || actual == null) {
            return 0.0;
        }
        var keys = new ArrayList<String>();
        keys.addAll(expected.keySet());
        actual.keySet().stream()
                .filter(key -> !expected.containsKey(key))
                .forEach(keys::add);
        if (keys.isEmpty()) {
            return 1.0;
        }

        var score = 0.0;
        for (var key : keys) {
            if (java.util.Objects.equals(expected.get(key), actual.get(key))) {
                score += 1.0 / keys.size();
            } else if (expected.get(key) instanceof Map<?, ?> expectedMap
                    && actual.get(key) instanceof Map<?, ?> actualMap) {
                score += compareMaps(asObjectMap(expectedMap), asObjectMap(actualMap)) / keys.size();
            }
        }
        return score;
    }

    private Map<String, Object> asObjectMap(Map<?, ?> map) {
        var values = new java.util.HashMap<String, Object>();
        map.forEach((key, value) -> values.put(String.valueOf(key), value));
        return values;
    }

    private ToolSelectionScore getToolSelectionScore(LlmTestCase testCase) {
        if (availableTools.isEmpty() || testCase.multimodal()) {
            return new ToolSelectionScore(
                    1.0,
                    "No available tools were provided to assess tool selection criteria");
        }
        if (model == null) {
            throw new UnsupportedOperationException("Tool Correctness generation requires a model provider");
        }
        var data = new ToolUseSchemas.UserInputAndTools(
                testCase.input(),
                testCase.actualOutput(),
                String.join(",", testCase.toolsCalled().stream().map(ToolCall::toString).toList()),
                String.join(",", availableTools.stream().map(ToolCall::toString).toList()),
                !testCase.toolsCalled().isEmpty());
        return ToolUseSchemas.parseToolSelectionScore(model.generate(ToolUsePrompts.toolSelectionScore(data)));
    }

    private String finalReason(String toolCallingReason, String toolSelectionReason) {
        return "[\n"
                + "\t Tool Calling Reason: " + toolCallingReason + "\n"
                + "\t Tool Selection Reason: " + toolSelectionReason + "\n"
                + "]\n";
    }

    private String toolCallingReason(List<ToolCall> toolsCalled, List<ToolCall> expectedTools, double toolCallingScore) {
        var expectedNames = expectedTools.stream().map(ToolCall::name).toList();
        var calledNames = toolsCalled.stream().map(ToolCall::name).toList();
        if (shouldExactMatch) {
            return (toolCallingScore == 1.0 ? "Exact match" : "Not an exact match")
                    + ": expected " + expectedNames + ", called " + calledNames + ". See details above.";
        }
        if (!shouldExactMatch && !shouldConsiderOrdering && toolCallingScore == 1.0) {
            return "All expected tools " + expectedNames + " were called (order not considered).";
        }
        if (shouldConsiderOrdering && toolCallingScore == 1.0) {
            return "Correct ordering: all expected tools " + expectedNames + " were called in the correct order.";
        }
        if (shouldConsiderOrdering) {
            var issues = new ArrayList<String>();
            var missing = expectedNames.stream()
                    .filter(expected -> !calledNames.contains(expected))
                    .toList();
            if (!missing.isEmpty()) {
                issues.add("missing tools " + missing);
            }
            var orderedNames = orderedMatchNames(toolsCalled, expectedTools);
            var outOfOrder = expectedNames.stream()
                    .filter(expectedName -> !orderedNames.contains(expectedName))
                    .toList();
            if (!outOfOrder.isEmpty()) {
                issues.add("out-of-order tools " + outOfOrder);
            }
            return "Incorrect tool usage: " + String.join(" and ", issues)
                    + "; expected " + expectedNames + ", called " + calledNames + ". See more details above.";
        }
        if (!shouldExactMatch && !shouldConsiderOrdering) {
            var missing = expectedTools.stream()
                    .filter(expected -> toolsCalled.stream().noneMatch(called -> called.equals(expected)))
                    .toList();
            return "Incomplete tool usage: missing tools " + missing
                    + "; expected " + expectedNames + ", called " + calledNames + ". See more details above.";
        }
        return success
                ? "Tool usage matched expected tools."
                : "Tool usage did not match expected tools; expected " + expectedNames + ", called " + calledNames + ".";
    }

    private List<String> orderedMatchNames(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        var expectedCount = expectedTools.size();
        var calledCount = toolsCalled.size();
        var dp = new double[expectedCount + 1][calledCount + 1];
        for (var i = 1; i <= expectedCount; i++) {
            for (var j = 1; j <= calledCount; j++) {
                var matchScore = toolMatchScore(toolsCalled.get(j - 1), expectedTools.get(i - 1));
                dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                if (matchScore > 0.0) {
                    dp[i][j] = Math.max(dp[i][j], dp[i - 1][j - 1] + matchScore);
                }
            }
        }

        var ordered = new ArrayList<String>();
        var i = expectedCount;
        var j = calledCount;
        while (i > 0 && j > 0) {
            if (dp[i][j] == dp[i - 1][j]) {
                i--;
            } else if (dp[i][j] == dp[i][j - 1]) {
                j--;
            } else {
                ordered.add(0, expectedTools.get(i - 1).name());
                i--;
                j--;
            }
        }
        return ordered;
    }

    private void requireTools(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.TOOLS_CALLED, SingleTurnParam.EXPECTED_TOOLS),
                name());
    }
}
