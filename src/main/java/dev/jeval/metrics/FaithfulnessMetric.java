package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.RetrievedContextData;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.FaithfulnessSchemas.FaithfulnessVerdict;
import java.util.List;

public class FaithfulnessMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final boolean penalizeAmbiguousClaims;
    private final Integer truthsExtractionLimit;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<String> truths = List.of();
    private List<String> claims = List.of();
    private List<FaithfulnessVerdict> verdicts = List.of();

    public FaithfulnessMetric() {
        this(0.5, true, false, false);
    }

    public FaithfulnessMetric(EvaluationModel model) {
        this(model, 0.5, true, false, false, null);
    }

    public FaithfulnessMetric(double threshold, boolean includeReason, boolean strictMode, boolean penalizeAmbiguousClaims) {
        this(null, threshold, includeReason, strictMode, penalizeAmbiguousClaims, null);
    }

    public FaithfulnessMetric(
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode,
            boolean penalizeAmbiguousClaims,
            Integer truthsExtractionLimit) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.penalizeAmbiguousClaims = penalizeAmbiguousClaims;
        this.truthsExtractionLimit = truthsExtractionLimit == null ? null : Math.max(truthsExtractionLimit, 0);
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());
        if (testCase.retrievalContext() == null) {
            throw new MissingTestCaseParamsException("'retrieval_context' cannot be None for the '" + name() + "' metric");
        }

        truths = List.copyOf(generateTruths(
                RetrievedContextData.textValues(testCase.retrievalContext()), testCase.multimodal()));
        claims = List.copyOf(generateClaims(testCase.actualOutput(), testCase.multimodal()));
        verdicts = claims.isEmpty() ? List.of() : List.copyOf(generateVerdicts(testCase.multimodal()));
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.multimodal()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Faithfulness";
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

    public List<String> truths() {
        return truths;
    }

    public List<String> claims() {
        return claims;
    }

    public List<FaithfulnessVerdict> verdicts() {
        return verdicts;
    }

    protected List<String> generateTruths(List<String> retrievalContext, boolean multimodal) {
        requireModel();
        return FaithfulnessSchemas.parseTruths(
                model.generate(FaithfulnessPrompts.generateTruths(retrievalContext, truthsExtractionLimit, multimodal)))
                .truths();
    }

    protected List<String> generateClaims(String actualOutput, boolean multimodal) {
        requireModel();
        return FaithfulnessSchemas.parseClaims(
                model.generate(FaithfulnessPrompts.generateClaims(actualOutput, multimodal))).claims();
    }

    protected List<FaithfulnessVerdict> generateVerdicts(boolean multimodal) {
        requireModel();
        return FaithfulnessSchemas.parseVerdicts(
                model.generate(FaithfulnessPrompts.generateVerdicts(truths, claims, multimodal))).verdicts();
    }

    protected String generateReason(boolean multimodal) {
        requireModel();
        var contradictions = verdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase())
                        || penalizeAmbiguousClaims && "idk".equals(verdict.verdict().strip().toLowerCase()))
                .map(verdict -> "idk".equals(verdict.verdict().strip().toLowerCase())
                        ? "(Ambiguous) " + verdict.reason()
                        : verdict.reason())
                .toList();
        return FaithfulnessSchemas.parseScoreReason(
                model.generate(FaithfulnessPrompts.generateReason(score, contradictions))).reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 1.0;
        }

        var faithfulnessCount = 0;
        for (var verdict : verdicts) {
            var value = verdict.verdict().strip().toLowerCase();
            if (!"no".equals(value)) {
                faithfulnessCount++;
            }
            if (penalizeAmbiguousClaims && "idk".equals(value)) {
                faithfulnessCount--;
            }
        }
        var calculatedScore = (double) faithfulnessCount / verdicts.size();
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Faithfulness generation requires a model provider");
        }
    }
}
