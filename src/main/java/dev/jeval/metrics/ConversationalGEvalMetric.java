package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import java.util.ArrayList;
import java.util.List;

public class ConversationalGEvalMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final String name;
    private final List<MultiTurnParam> evaluationParams;
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

    public ConversationalGEvalMetric(String name, List<MultiTurnParam> evaluationParams, String criteria) {
        this(null, name, evaluationParams, criteria, null, null, 0.5, false);
    }

    public ConversationalGEvalMetric(
            EvaluationModel model,
            String name,
            List<MultiTurnParam> evaluationParams,
            String criteria) {
        this(model, name, evaluationParams, criteria, null, null, 0.5, false);
    }

    public ConversationalGEvalMetric(
            EvaluationModel model,
            String name,
            List<MultiTurnParam> evaluationParams,
            String criteria,
            List<String> evaluationSteps,
            List<GEvalUtils.Rubric> rubric,
            double threshold,
            boolean strictMode) {
        if (evaluationParams == null || evaluationParams.isEmpty()) {
            throw new IllegalArgumentException("evaluation_params cannot be an empty list.");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Conversational GEval threshold must be finite");
        }
        GEvalUtils.validateCriteriaAndEvaluationSteps(criteria, evaluationSteps);
        this.model = model;
        this.name = name;
        this.evaluationParams = withTurnDefaults(evaluationParams);
        this.criteria = criteria;
        this.evaluationSteps = evaluationSteps == null ? null : List.copyOf(evaluationSteps);
        this.rubric = GEvalUtils.validateAndSortRubrics(rubric);
        this.scoreRange = GEvalUtils.getScoreRange(this.rubric);
        this.scoreRangeSpan = scoreRange.get(1) - scoreRange.get(0);
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(testCase, evaluationParams, name());
        if (evaluationSteps == null) {
            evaluationSteps = List.copyOf(generateEvaluationSteps());
        }
        var reasonScore = evaluate(testCase);
        score = strictMode ? reasonScore.score() : (reasonScore.score() - scoreRange.get(0)) / scoreRangeSpan;
        reason = reasonScore.reason();
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return name + " [Conversational GEval]";
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

    protected List<String> generateEvaluationSteps() {
        requireModel();
        return GEvalSchemas.parseSteps(model.generate(GEvalPrompts.generateConversationalEvaluationSteps(
                criteria, GEvalUtils.constructConversationalTurnParamsString(evaluationParams)))).steps();
    }

    protected GEvalSchemas.ReasonScore evaluate(ConversationalTestCase testCase) {
        requireModel();
        var prompt = strictMode
                ? GEvalPrompts.generateStrictEvaluationResults(
                        evaluationSteps, testCaseContent(testCase),
                        GEvalUtils.constructConversationalTurnParamsString(evaluationParams),
                        false)
                : GEvalPrompts.generateConversationalEvaluationResults(
                        evaluationSteps,
                        testCaseContent(testCase),
                        GEvalUtils.constructConversationalTurnParamsString(evaluationParams),
                        GEvalUtils.formatRubrics(rubric),
                        scoreRange);
        return GEvalSchemas.parseReasonScore(model.generate(prompt));
    }

    private String testCaseContent(ConversationalTestCase testCase) {
        return GEvalUtils.constructNonTurnsTestCaseString(evaluationParams, testCase)
                + "Turns:\n"
                + testCase.turns().stream()
                        .map(turn -> MetricUtils.convertTurnToDict(turn, evaluationParams).toString())
                        .toList();
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Conversational GEval generation requires a model provider");
        }
    }

    private static List<MultiTurnParam> withTurnDefaults(List<MultiTurnParam> params) {
        var result = new ArrayList<>(params);
        if (!result.contains(MultiTurnParam.CONTENT)) {
            result.add(MultiTurnParam.CONTENT);
        }
        if (!result.contains(MultiTurnParam.ROLE)) {
            result.add(MultiTurnParam.ROLE);
        }
        return List.copyOf(result);
    }
}
