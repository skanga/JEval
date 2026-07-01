package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.PromptAlignmentSchemas.PromptAlignmentVerdict;
import java.util.List;

public class PromptAlignmentMetric implements Metric {
    private final EvaluationModel model;
    private final List<String> promptInstructions;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<PromptAlignmentVerdict> verdicts = List.of();

    public PromptAlignmentMetric(List<String> promptInstructions) {
        this(promptInstructions, 0.5, true, false);
    }

    public PromptAlignmentMetric(EvaluationModel model, List<String> promptInstructions) {
        this(model, promptInstructions, 0.5, true, false);
    }

    public PromptAlignmentMetric(
            List<String> promptInstructions,
            double threshold,
            boolean includeReason,
            boolean strictMode) {
        this(null, promptInstructions, threshold, includeReason, strictMode);
    }

    public PromptAlignmentMetric(
            EvaluationModel model,
            List<String> promptInstructions,
            double threshold,
            boolean includeReason,
            boolean strictMode) {
        if (promptInstructions == null || promptInstructions.isEmpty()) {
            throw new IllegalArgumentException("'prompt_instructions' must not be empty.");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Prompt Alignment threshold must be finite");
        }
        this.model = model;
        this.promptInstructions = List.copyOf(promptInstructions);
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), name());

        verdicts = List.copyOf(generateVerdicts(testCase.input(), testCase.actualOutput()));
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.input(), testCase.actualOutput()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Prompt Alignment";
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

    public List<String> promptInstructions() {
        return promptInstructions;
    }

    public List<PromptAlignmentVerdict> verdicts() {
        return verdicts;
    }

    protected List<PromptAlignmentVerdict> generateVerdicts(String input, String actualOutput) {
        requireModel();
        return PromptAlignmentSchemas.parseVerdicts(model.generate(
                PromptAlignmentPrompts.generateVerdicts(promptInstructions, input, actualOutput))).verdicts();
    }

    protected String generateReason(String input, String actualOutput) {
        requireModel();
        var unalignmentReasons = verdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase()))
                .map(PromptAlignmentVerdict::reason)
                .toList();
        return PromptAlignmentSchemas.parseScoreReason(model.generate(
                PromptAlignmentPrompts.generateReason(score, unalignmentReasons, input, actualOutput))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 1.0;
        }
        var alignedCount = 0;
        for (var verdict : verdicts) {
            if (!"no".equals(verdict.verdict().strip().toLowerCase())) {
                alignedCount++;
            }
        }
        var calculatedScore = (double) alignedCount / verdicts.size();
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Prompt Alignment generation requires a model provider");
        }
    }
}
