package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.RetrievedContextData;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.ContextualRecallSchemas.VerdictWithExpectedOutput;
import java.util.List;

public class ContextualRecallMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<VerdictWithExpectedOutput> verdicts = List.of();

    public ContextualRecallMetric() {
        this(0.5, true, false);
    }

    public ContextualRecallMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public ContextualRecallMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public ContextualRecallMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.EXPECTED_OUTPUT),
                name());
        if (testCase.retrievalContext() == null) {
            throw new MissingTestCaseParamsException("'retrieval_context' cannot be None for the '" + name() + "' metric");
        }

        verdicts = List.copyOf(generateVerdicts(
                testCase.expectedOutput(), RetrievedContextData.textValues(testCase.retrievalContext()),
                testCase.multimodal()));
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.expectedOutput(), testCase.multimodal()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Contextual Recall";
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

    public List<VerdictWithExpectedOutput> verdicts() {
        return verdicts;
    }

    protected List<VerdictWithExpectedOutput> generateVerdicts(
            String expectedOutput, List<String> retrievalContext, boolean multimodal) {
        requireModel();
        return ContextualRecallSchemas.parseVerdicts(
                        model.generate(ContextualRecallPrompts.generateVerdicts(
                                expectedOutput, retrievalContext, multimodal)))
                .verdicts()
                .stream()
                .map(verdict -> new VerdictWithExpectedOutput(
                        verdict.verdict(), verdict.reason(), expectedOutput))
                .toList();
    }

    protected String generateReason(String expectedOutput, boolean multimodal) {
        requireModel();
        var supportiveReasons = verdicts.stream()
                .filter(verdict -> "yes".equals(verdict.verdict().toLowerCase()))
                .map(VerdictWithExpectedOutput::reason)
                .toList();
        var unsupportiveReasons = verdicts.stream()
                .filter(verdict -> !"yes".equals(verdict.verdict().toLowerCase()))
                .map(VerdictWithExpectedOutput::reason)
                .toList();
        return ContextualRecallSchemas.parseScoreReason(
                model.generate(ContextualRecallPrompts.generateReason(
                        expectedOutput, score, supportiveReasons, unsupportiveReasons, multimodal))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 0.0;
        }
        var recalled = 0;
        for (var verdict : verdicts) {
            if ("yes".equals(verdict.verdict().toLowerCase())) {
                recalled++;
            }
        }
        var calculatedScore = (double) recalled / verdicts.size();
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Contextual recall generation requires a model provider");
        }
    }
}
