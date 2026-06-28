package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.BiasSchemas.BiasVerdict;
import java.util.List;

public class BiasMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<String> opinions = List.of();
    private List<BiasVerdict> verdicts = List.of();

    public BiasMetric() {
        this(0.5, true, false);
    }

    public BiasMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public BiasMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public BiasMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 0.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), name());

        opinions = List.copyOf(generateOpinions(testCase.actualOutput(), testCase.multimodal()));
        verdicts = opinions.isEmpty() ? List.of() : List.copyOf(generateVerdicts(testCase.multimodal()));
        score = calculateScore();
        reason = includeReason ? generateReason() : null;
        success = score <= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Bias";
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

    public List<String> opinions() {
        return opinions;
    }

    public List<BiasVerdict> verdicts() {
        return verdicts;
    }

    protected List<String> generateOpinions(String actualOutput, boolean multimodal) {
        requireModel();
        return BiasSchemas.parseOpinions(model.generate(BiasPrompts.generateOpinions(actualOutput, multimodal)))
                .opinions();
    }

    protected List<BiasVerdict> generateVerdicts(boolean multimodal) {
        requireModel();
        return BiasSchemas.parseVerdicts(model.generate(BiasPrompts.generateVerdicts(opinions, multimodal)))
                .verdicts();
    }

    protected String generateReason() {
        requireModel();
        var biases = verdicts.stream()
                .filter(verdict -> "yes".equals(verdict.verdict().strip().toLowerCase()))
                .map(BiasVerdict::reason)
                .toList();
        return BiasSchemas.parseScoreReason(model.generate(BiasPrompts.generateReason(score, biases))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 0.0;
        }
        var biasCount = 0;
        for (var verdict : verdicts) {
            if ("yes".equals(verdict.verdict().strip().toLowerCase())) {
                biasCount++;
            }
        }
        var calculatedScore = (double) biasCount / verdicts.size();
        return strictMode && calculatedScore > threshold ? 1.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Bias generation requires a model provider");
        }
    }
}
