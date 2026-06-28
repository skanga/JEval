package dev.jeval.metrics;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.TaskCompletionSchemas.TaskAndOutcome;
import dev.jeval.metrics.TaskCompletionSchemas.TaskCompletionVerdict;
import java.util.List;

public class TaskCompletionMetric implements Metric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private final boolean taskProvided;
    private String task;
    private String outcome;
    private double verdict;
    private double score;
    private String reason;
    private boolean success;

    public TaskCompletionMetric() {
        this(0.5, null, true, false);
    }

    public TaskCompletionMetric(EvaluationModel model) {
        this(model, 0.5, null, true, false);
    }

    public TaskCompletionMetric(double threshold, String task, boolean includeReason, boolean strictMode) {
        this(null, threshold, task, includeReason, strictMode);
    }

    public TaskCompletionMetric(
            EvaluationModel model,
            double threshold,
            String task,
            boolean includeReason,
            boolean strictMode) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
        this.task = task;
        this.taskProvided = task != null;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(
                testCase, List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT), name());

        var taskAndOutcome = extractTaskAndOutcome(testCase);
        outcome = taskAndOutcome.outcome();
        if (!taskProvided) {
            task = taskAndOutcome.task();
        }
        var generatedVerdict = generateVerdict();
        verdict = generatedVerdict.verdict();
        reason = includeReason ? generatedVerdict.reason() : null;
        score = calculateScore();
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Task Completion";
    }

    public String task() {
        return task;
    }

    public String outcome() {
        return outcome;
    }

    public double verdict() {
        return verdict;
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

    protected TaskAndOutcome extractTaskAndOutcome(LlmTestCase testCase) {
        requireModel();
        return TaskCompletionSchemas.parseTaskAndOutcome(model.generate(TaskCompletionPrompts.extractTaskAndOutcome(
                testCase.input(), testCase.actualOutput(), testCase.toolsCalled())));
    }

    protected TaskCompletionVerdict generateVerdict() {
        requireModel();
        return TaskCompletionSchemas.parseVerdict(
                model.generate(TaskCompletionPrompts.generateVerdict(task, outcome)));
    }

    private double calculateScore() {
        return strictMode && verdict < threshold ? 0.0 : verdict;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Task Completion generation requires a model provider");
        }
    }
}
