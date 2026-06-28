package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.ToolCall;
import dev.jeval.metrics.ArgumentCorrectnessSchemas.ArgumentCorrectnessVerdict;
import java.util.List;

public class ArgumentCorrectnessMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<ArgumentCorrectnessVerdict> verdicts = List.of();

    public ArgumentCorrectnessMetric() {
        this(0.5, true, false);
    }

    public ArgumentCorrectnessMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public ArgumentCorrectnessMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public ArgumentCorrectnessMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.TOOLS_CALLED), name());

        if (testCase.toolsCalled().isEmpty()) {
            verdicts = List.of();
            score = 1.0;
            reason = "No tool calls provided";
        } else {
            verdicts = List.copyOf(generateVerdicts(testCase.input(), testCase.toolsCalled(), testCase.multimodal()));
            score = calculateScore();
            reason = includeReason ? generateReason(testCase.input(), testCase.multimodal()) : null;
        }
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Argument Correctness";
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

    public List<ArgumentCorrectnessVerdict> verdicts() {
        return verdicts;
    }

    protected List<ArgumentCorrectnessVerdict> generateVerdicts(
            String input,
            List<ToolCall> toolsCalled,
            boolean multimodal) {
        requireModel();
        return ArgumentCorrectnessSchemas.parseVerdicts(model.generate(
                ArgumentCorrectnessPrompts.generateVerdicts(input, toolsCalled, multimodal))).verdicts();
    }

    protected String generateReason(String input, boolean multimodal) {
        requireModel();
        var incorrectReasons = verdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase()))
                .map(ArgumentCorrectnessVerdict::reason)
                .toList();
        return ArgumentCorrectnessSchemas.parseScoreReason(model.generate(
                ArgumentCorrectnessPrompts.generateReason(score, incorrectReasons, input, multimodal))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var correctCount = 0;
        for (var verdict : verdicts) {
            if (!"no".equals(verdict.verdict().strip().toLowerCase())) {
                correctCount++;
            }
        }
        var calculatedScore = (double) correctCount / verdicts.size();
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Argument Correctness generation requires a model provider");
        }
    }
}
