package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.PlanAdherenceSchemas.PlanAdherenceScore;
import dev.jeval.metrics.PlanQualitySchemas.AgentPlan;
import dev.jeval.metrics.StepEfficiencySchemas.Task;
import java.util.List;
import java.util.Map;

public class PlanAdherenceMetric implements Metric {
    private static final String EMPTY_PLAN_REASON = "There were no plans to evaluate within the trace of your agent's execution.";

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private String task;
    private List<String> plan;
    private double score;
    private String reason;
    private boolean success;

    public PlanAdherenceMetric() {
        this(0.5, true, false);
    }

    public PlanAdherenceMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public PlanAdherenceMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public PlanAdherenceMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Plan Adherence threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), name());
        if (testCase.trace() == null) {
            throw new MissingTestCaseParamsException("'trace' cannot be None for the '" + name() + "' metric");
        }
        task = extractTask(testCase.trace()).task();
        plan = extractPlan(testCase.trace()).plan();
        if (plan.isEmpty()) {
            score = 1.0;
            reason = includeReason ? EMPTY_PLAN_REASON : null;
        } else {
            var generatedScore = generateScore(task, plan, testCase.trace());
            score = strictMode && generatedScore.score() < threshold ? 0.0 : generatedScore.score();
            reason = includeReason ? generatedScore.reason() : null;
        }
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Plan Adherence";
    }

    public String task() {
        return task;
    }

    public List<String> plan() {
        return plan;
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

    protected Task extractTask(Map<String, Object> trace) {
        requireModel();
        return StepEfficiencySchemas.parseTask(model.generate(StepEfficiencyPrompts.extractTaskFromTrace(trace)));
    }

    protected AgentPlan extractPlan(Map<String, Object> trace) {
        requireModel();
        return PlanQualitySchemas.parseAgentPlan(model.generate(PlanQualityPrompts.extractPlanFromTrace(trace)));
    }

    protected PlanAdherenceScore generateScore(String task, List<String> plan, Map<String, Object> trace) {
        requireModel();
        return PlanAdherenceSchemas.parseScore(model.generate(PlanAdherencePrompts.evaluateAdherence(task, plan, trace)));
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Plan Adherence generation requires a model provider");
        }
    }

}
