package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.HallucinationSchemas.HallucinationVerdict;
import java.util.List;

public class HallucinationMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<HallucinationVerdict> verdicts = List.of();

    public HallucinationMetric() {
        this(0.5, true, false);
    }

    public HallucinationMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public HallucinationMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public HallucinationMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 0.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());
        if (testCase.context() == null) {
            throw new MissingTestCaseParamsException("'context' cannot be None for the '" + name() + "' metric");
        }

        verdicts = List.copyOf(generateVerdicts(testCase.actualOutput(), testCase.context()));
        score = calculateScore();
        reason = includeReason ? generateReason() : null;
        success = score <= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Hallucination";
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

    public List<HallucinationVerdict> verdicts() {
        return verdicts;
    }

    protected List<HallucinationVerdict> generateVerdicts(String actualOutput, List<String> context) {
        requireModel();
        return HallucinationSchemas.parseVerdicts(
                model.generate(HallucinationPrompts.generateVerdicts(actualOutput, context))).verdicts();
    }

    protected String generateReason() {
        requireModel();
        var factualAlignments = verdicts.stream()
                .filter(verdict -> "yes".equals(verdict.verdict().strip().toLowerCase()))
                .map(HallucinationVerdict::reason)
                .toList();
        var contradictions = verdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase()))
                .map(HallucinationVerdict::reason)
                .toList();
        return HallucinationSchemas.parseScoreReason(
                model.generate(HallucinationPrompts.generateReason(score, factualAlignments, contradictions))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 0.0;
        }
        var hallucinationCount = 0;
        for (var verdict : verdicts) {
            if ("no".equals(verdict.verdict().strip().toLowerCase())) {
                hallucinationCount++;
            }
        }
        var calculatedScore = (double) hallucinationCount / verdicts.size();
        return strictMode && calculatedScore > threshold ? 1.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Hallucination generation requires a model provider");
        }
    }
}
