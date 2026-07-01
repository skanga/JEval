package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.GoalAccuracySchemas.GoalScore;
import dev.jeval.metrics.GoalAccuracySchemas.GoalSteps;
import dev.jeval.metrics.GoalAccuracySchemas.PlanScore;
import java.util.ArrayList;
import java.util.List;

public class GoalAccuracyMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<GoalSteps> goalSteps;
    private List<GoalScore> goalScores;
    private List<PlanScore> planScores;
    private double score;
    private String reason;
    private boolean success;

    public GoalAccuracyMetric() {
        this(0.5, true, false);
    }

    public GoalAccuracyMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public GoalAccuracyMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public GoalAccuracyMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Goal Accuracy threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name());
        goalSteps = buildGoalSteps(MetricUtils.getUnitInteractions(testCase.turns()));
        goalScores = new ArrayList<>();
        planScores = new ArrayList<>();
        for (var goalStep : goalSteps) {
            goalScores.add(getGoalScore(goalStep.userGoal(), goalStep.stepsTaken(), testCase.multimodal()));
            planScores.add(getPlanScore(goalStep.userGoal(), goalStep.stepsTaken(), testCase.multimodal()));
        }
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? generateReason(testCase.multimodal()) : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Goal Accuracy";
    }

    public List<GoalSteps> goalSteps() {
        return goalSteps;
    }

    public List<GoalScore> goalScores() {
        return goalScores;
    }

    public List<PlanScore> planScores() {
        return planScores;
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

    protected List<GoalSteps> buildGoalSteps(List<List<Turn>> unitInteractions) {
        var results = new ArrayList<GoalSteps>();
        for (var unitInteraction : unitInteractions) {
            var userGoal = new StringBuilder("User messages:\n");
            for (var turn : unitInteraction) {
                if ("user".equals(turn.role())) {
                    userGoal.append(turn.content()).append('\n');
                } else {
                    break;
                }
            }
            var stepsTaken = new ArrayList<String>();
            var assistantMessages = new StringBuilder("Assistant messages:\n");
            for (var turn : unitInteraction.subList(1, unitInteraction.size())) {
                if ("assistant".equals(turn.role())) {
                    assistantMessages.append(turn.content()).append('\n');
                    if (turn.toolsCalled() != null && !turn.toolsCalled().isEmpty()) {
                        assistantMessages.append("Tools called:\n").append(turn.toolsCalled()).append('\n');
                    }
                    stepsTaken.add(assistantMessages.toString());
                }
            }
            results.add(new GoalSteps(userGoal.toString(), stepsTaken));
        }
        return results;
    }

    protected GoalScore getGoalScore(String userGoal, List<String> stepsTaken, boolean multimodal) {
        requireModel();
        return GoalAccuracySchemas.parseGoalScore(
                model.generate(GoalAccuracyPrompts.goalScore(userGoal, stepsTaken, multimodal)));
    }

    protected PlanScore getPlanScore(String userGoal, List<String> stepsTaken, boolean multimodal) {
        requireModel();
        return GoalAccuracySchemas.parsePlanScore(
                model.generate(GoalAccuracyPrompts.planScore(userGoal, stepsTaken, multimodal)));
    }

    protected String generateReason(boolean multimodal) {
        requireModel();
        return model.generate(GoalAccuracyPrompts.finalReason(score, threshold, goalScores, planScores, multimodal));
    }

    private double calculateScore() {
        var goalAvg = goalScores.stream().mapToDouble(GoalScore::score).average().orElse(0.0);
        var planAvg = planScores.stream().mapToDouble(PlanScore::score).average().orElse(0.0);
        var rawScore = (goalAvg + planAvg) / 2.0;
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Goal Accuracy generation requires a model provider");
        }
    }
}
