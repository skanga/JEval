package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.ToolCall;
import dev.jeval.Turn;
import dev.jeval.metrics.ToolUseSchemas.ArgumentCorrectnessScore;
import dev.jeval.metrics.ToolUseSchemas.Reason;
import dev.jeval.metrics.ToolUseSchemas.ToolSelectionScore;
import dev.jeval.metrics.ToolUseSchemas.UserInputAndTools;
import java.util.ArrayList;
import java.util.List;

public class ToolUseMetric implements ConversationalMetric {
    private final List<ToolCall> availableTools;
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<UserInputAndTools> userInputAndTools;
    private List<ToolSelectionScore> toolSelectionScores;
    private List<ArgumentCorrectnessScore> argumentCorrectnessScores;
    private double score;
    private String reason;
    private boolean success;

    public ToolUseMetric(List<ToolCall> availableTools) {
        this(availableTools, 0.5, true, false);
    }

    public ToolUseMetric(List<ToolCall> availableTools, EvaluationModel model) {
        this(availableTools, model, 0.5, true, false);
    }

    public ToolUseMetric(List<ToolCall> availableTools, double threshold, boolean includeReason, boolean strictMode) {
        this(availableTools, null, threshold, includeReason, strictMode);
    }

    public ToolUseMetric(
            List<ToolCall> availableTools,
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Tool Use threshold must be finite");
        }
        this.availableTools = List.copyOf(availableTools);
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name());
        userInputAndTools = getUserInputAndTools(MetricUtils.getUnitInteractions(testCase.turns()));
        toolSelectionScores = new ArrayList<>();
        argumentCorrectnessScores = new ArrayList<>();
        for (var item : userInputAndTools) {
            toolSelectionScores.add(getToolSelectionScore(item));
            if (item.toolsUsed()) {
                argumentCorrectnessScores.add(getArgumentCorrectnessScore(item));
            }
        }
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? generateToolSelectionReason().reason() + "\n" + generateArgumentCorrectnessReason().reason() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Tool Use";
    }

    public List<UserInputAndTools> userInputAndTools() {
        return userInputAndTools;
    }

    public List<ToolSelectionScore> toolSelectionScores() {
        return toolSelectionScores;
    }

    public List<ArgumentCorrectnessScore> argumentCorrectnessScores() {
        return argumentCorrectnessScores;
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

    protected List<UserInputAndTools> getUserInputAndTools(List<List<Turn>> unitInteractions) {
        var values = new ArrayList<UserInputAndTools>();
        var available = String.join(",", availableTools.stream().map(ToolCall::toString).toList());
        for (var unitInteraction : unitInteractions) {
            if (unitInteraction.size() < 2) {
                continue;
            }
            var userMessages = new StringBuilder();
            var assistantMessages = new StringBuilder();
            var toolsCalled = new ArrayList<ToolCall>();
            for (var turn : unitInteraction) {
                if ("user".equals(turn.role())) {
                    userMessages.append(turn.content()).append('\n');
                } else {
                    break;
                }
            }
            for (var turn : unitInteraction.subList(1, unitInteraction.size())) {
                if ("assistant".equals(turn.role())) {
                    assistantMessages.append(turn.content()).append('\n');
                    if (turn.toolsCalled() != null) {
                        toolsCalled.addAll(turn.toolsCalled());
                    }
                }
            }
            values.add(new UserInputAndTools(
                    userMessages.toString(),
                    assistantMessages.toString(),
                    String.join(",", toolsCalled.stream().map(ToolCall::toString).toList()),
                    available,
                    !toolsCalled.isEmpty()));
        }
        return values;
    }

    protected ToolSelectionScore getToolSelectionScore(UserInputAndTools data) {
        requireModel();
        return ToolUseSchemas.parseToolSelectionScore(model.generate(ToolUsePrompts.toolSelectionScore(data)));
    }

    protected ArgumentCorrectnessScore getArgumentCorrectnessScore(UserInputAndTools data) {
        requireModel();
        return ToolUseSchemas.parseArgumentCorrectnessScore(model.generate(ToolUsePrompts.argumentCorrectnessScore(data)));
    }

    protected Reason generateToolSelectionReason() {
        requireModel();
        return ToolUseSchemas.parseReason(model.generate(ToolUsePrompts.toolSelectionReason(
                toolSelectionScores, score, threshold)));
    }

    protected Reason generateArgumentCorrectnessReason() {
        requireModel();
        return ToolUseSchemas.parseReason(model.generate(ToolUsePrompts.argumentCorrectnessReason(
                argumentCorrectnessScores, score, threshold)));
    }

    private double calculateScore() {
        var selectionAvg = toolSelectionScores.stream().mapToDouble(ToolSelectionScore::score).average().orElse(0.0);
        var argumentAvg = argumentCorrectnessScores.stream()
                .mapToDouble(ArgumentCorrectnessScore::score)
                .average()
                .orElse(0.0);
        var rawScore = Math.min(selectionAvg, argumentAvg);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Tool Use generation requires a model provider");
        }
    }
}
