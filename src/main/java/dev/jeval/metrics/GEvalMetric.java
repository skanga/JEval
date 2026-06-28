package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import java.util.List;

public class GEvalMetric implements Metric {
    private final EvaluationModel model;
    private final String name;
    private final List<SingleTurnParam> evaluationParams;
    private final String criteria;
    private final List<GEvalUtils.Rubric> rubric;
    private final List<Integer> scoreRange;
    private final double scoreRangeSpan;
    private final boolean strictMode;
    private final double threshold;
    private List<String> evaluationSteps;
    private double score;
    private String reason;
    private boolean success;

    public GEvalMetric(String name, List<SingleTurnParam> evaluationParams, String criteria) {
        this(null, name, evaluationParams, criteria, null, null, 0.5, false);
    }

    public GEvalMetric(
            EvaluationModel model,
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria) {
        this(model, name, evaluationParams, criteria, null, null, 0.5, false);
    }

    public GEvalMetric(
            EvaluationModel model,
            String name,
            List<SingleTurnParam> evaluationParams,
            String criteria,
            List<String> evaluationSteps,
            List<GEvalUtils.Rubric> rubric,
            double threshold,
            boolean strictMode) {
        if (evaluationParams == null || evaluationParams.isEmpty()) {
            throw new IllegalArgumentException("evaluation_params cannot be an empty list.");
        }
        GEvalUtils.validateCriteriaAndEvaluationSteps(criteria, evaluationSteps);
        this.model = model;
        this.name = name;
        this.evaluationParams = List.copyOf(evaluationParams);
        this.criteria = criteria;
        this.evaluationSteps = evaluationSteps == null ? null : List.copyOf(evaluationSteps);
        this.rubric = GEvalUtils.validateAndSortRubrics(rubric);
        this.scoreRange = GEvalUtils.getScoreRange(this.rubric);
        this.scoreRangeSpan = scoreRange.get(1) - scoreRange.get(0);
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(testCase, evaluationParams, name());
        if (evaluationSteps == null) {
            evaluationSteps = List.copyOf(generateEvaluationSteps(testCase.multimodal()));
        }
        var reasonScore = evaluate(testCase);
        score = strictMode ? reasonScore.score() : (reasonScore.score() - scoreRange.get(0)) / scoreRangeSpan;
        reason = reasonScore.reason();
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return name + " [GEval]";
    }

    public List<String> evaluationSteps() {
        return evaluationSteps;
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

    protected List<String> generateEvaluationSteps(boolean multimodal) {
        requireModel();
        return GEvalSchemas.parseSteps(model.generate(GEvalPrompts.generateEvaluationSteps(
                criteria, GEvalUtils.constructParamsString(evaluationParams), multimodal))).steps();
    }

    protected GEvalSchemas.ReasonScore evaluate(LlmTestCase testCase) {
        requireModel();
        var testCaseContent = GEvalUtils.constructTestCaseString(evaluationParams, testCase);
        var parameters = GEvalUtils.constructParamsString(evaluationParams);
        var prompt = strictMode
                ? GEvalPrompts.generateStrictEvaluationResults(
                        evaluationSteps, testCaseContent, parameters, testCase.multimodal())
                : GEvalPrompts.generateEvaluationResults(
                        evaluationSteps,
                        testCaseContent,
                        parameters,
                        GEvalUtils.formatRubrics(rubric),
                        scoreRange,
                        testCase.multimodal());
        return GEvalSchemas.parseReasonScore(model.generate(prompt));
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("GEval generation requires a model provider");
        }
    }
}
