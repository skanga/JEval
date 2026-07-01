package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.StepEfficiencySchemas.EfficiencyVerdict;
import dev.jeval.metrics.StepEfficiencySchemas.Task;
import java.util.List;
import java.util.Map;

public class StepEfficiencyMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private String task;
    private double score;
    private String reason;
    private boolean success;

    public StepEfficiencyMetric() {
        this(0.5, true, false);
    }

    public StepEfficiencyMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public StepEfficiencyMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public StepEfficiencyMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Step Efficiency threshold must be finite");
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
        var verdict = generateVerdict(task, testCase.trace());
        score = strictMode && verdict.score() < threshold ? 0.0 : verdict.score();
        reason = includeReason ? verdict.reason() : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Step Efficiency";
    }

    public String task() {
        return task;
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

    protected EfficiencyVerdict generateVerdict(String task, Map<String, Object> trace) {
        requireModel();
        return StepEfficiencySchemas.parseVerdict(
                model.generate(StepEfficiencyPrompts.getExecutionEfficiency(task, trace)));
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Step Efficiency generation requires a model provider");
        }
    }

}
