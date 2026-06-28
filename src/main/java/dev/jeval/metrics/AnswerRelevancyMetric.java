package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.AnswerRelevancySchemas.AnswerRelevancyVerdict;
import java.util.List;

public class AnswerRelevancyMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private double score;
    private String reason;
    private boolean success;
    private List<String> statements = List.of();
    private List<AnswerRelevancyVerdict> verdicts = List.of();

    public AnswerRelevancyMetric() {
        this(0.5, true, false);
    }

    public AnswerRelevancyMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public AnswerRelevancyMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public AnswerRelevancyMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                name());

        statements = List.copyOf(generateStatements(testCase.actualOutput(), testCase.multimodal()));
        verdicts = statements.isEmpty()
                ? List.of()
                : List.copyOf(generateVerdicts(testCase.input(), testCase.multimodal()));
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.input(), testCase.multimodal()) : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Answer Relevancy";
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

    public List<String> statements() {
        return statements;
    }

    public List<AnswerRelevancyVerdict> verdicts() {
        return verdicts;
    }

    protected List<String> generateStatements(String actualOutput, boolean multimodal) {
        requireModel();
        return AnswerRelevancySchemas.parseStatements(
                model.generate(AnswerRelevancyPrompts.generateStatements(actualOutput, multimodal))).statements();
    }

    protected List<AnswerRelevancyVerdict> generateVerdicts(String input, boolean multimodal) {
        requireModel();
        return AnswerRelevancySchemas.parseVerdicts(
                model.generate(AnswerRelevancyPrompts.generateVerdicts(input, statements, multimodal))).verdicts();
    }

    protected String generateReason(String input, boolean multimodal) {
        requireModel();
        var irrelevantStatements = verdicts.stream()
                .filter(verdict -> "no".equals(verdict.verdict().strip().toLowerCase()))
                .map(AnswerRelevancyVerdict::reason)
                .toList();
        return AnswerRelevancySchemas.parseScoreReason(
                model.generate(AnswerRelevancyPrompts.generateReason(input, score, irrelevantStatements, multimodal)))
                .reason();
    }

    private double calculateScore() {
        if (verdicts.isEmpty()) {
            return 1.0;
        }

        var relevantCount = 0;
        for (var verdict : verdicts) {
            if (!"no".equals(verdict.verdict().strip().toLowerCase())) {
                relevantCount++;
            }
        }
        var calculatedScore = (double) relevantCount / verdicts.size();
        return strictMode && calculatedScore < threshold ? 0.0 : calculatedScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Answer relevancy generation requires a model provider");
        }
    }
}
