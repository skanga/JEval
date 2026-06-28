package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.RetrievedContextData;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdict;
import dev.jeval.metrics.ContextualRelevancySchemas.ContextualRelevancyVerdicts;
import java.util.ArrayList;
import java.util.List;

public class ContextualRelevancyMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<ContextualRelevancyVerdicts> verdictsList = List.of();

    public ContextualRelevancyMetric() {
        this(0.5, true, false);
    }

    public ContextualRelevancyMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public ContextualRelevancyMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public ContextualRelevancyMetric(
            EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(testCase, List.of(SingleTurnParam.INPUT), name());
        if (testCase.retrievalContext() == null) {
            throw new MissingTestCaseParamsException("'retrieval_context' cannot be None for the '" + name() + "' metric");
        }

        var generated = new ArrayList<ContextualRelevancyVerdicts>();
        for (var context : RetrievedContextData.textValues(testCase.retrievalContext())) {
            generated.add(generateVerdicts(testCase.input(), context, testCase.multimodal()));
        }
        verdictsList = List.copyOf(generated);
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.input(), testCase.multimodal()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Contextual Relevancy";
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

    public List<ContextualRelevancyVerdicts> verdictsList() {
        return verdictsList;
    }

    protected ContextualRelevancyVerdicts generateVerdicts(String input, String context, boolean multimodal) {
        requireModel();
        return ContextualRelevancySchemas.parseVerdicts(
                model.generate(ContextualRelevancyPrompts.generateVerdicts(input, context, multimodal)));
    }

    protected String generateReason(String input, boolean multimodal) {
        requireModel();
        var irrelevantStatements = new ArrayList<String>();
        var relevantStatements = new ArrayList<String>();
        for (var verdicts : verdictsList) {
            for (var verdict : verdicts.verdicts()) {
                if ("no".equals(verdict.verdict().toLowerCase())) {
                    irrelevantStatements.add(verdict.reason());
                } else {
                    relevantStatements.add(verdict.statement());
                }
            }
        }
        return ContextualRelevancySchemas.parseScoreReason(
                model.generate(ContextualRelevancyPrompts.generateReason(
                        input, score, irrelevantStatements, relevantStatements, multimodal))).reason();
    }

    private double calculateScore() {
        var totalVerdicts = 0;
        var relevantStatements = 0;
        for (var verdicts : verdictsList) {
            for (ContextualRelevancyVerdict verdict : verdicts.verdicts()) {
                totalVerdicts++;
                if ("yes".equals(verdict.verdict().toLowerCase())) {
                    relevantStatements++;
                }
            }
        }
        if (totalVerdicts == 0) {
            return 0.0;
        }
        var calculatedScore = (double) relevantStatements / totalVerdicts;
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Contextual relevancy generation requires a model provider");
        }
    }
}
