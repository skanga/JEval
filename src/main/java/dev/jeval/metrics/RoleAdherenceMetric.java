package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.RoleAdherenceSchemas.OutOfCharacterResponseVerdict;
import dev.jeval.metrics.RoleAdherenceSchemas.OutOfCharacterResponseVerdicts;
import dev.jeval.metrics.RoleAdherenceSchemas.RoleAdherenceScoreReason;
import java.util.List;

public class RoleAdherenceMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private OutOfCharacterResponseVerdicts outOfCharacterVerdicts;
    private double score;
    private String reason;
    private boolean success;

    public RoleAdherenceMetric() {
        this(0.5, true, false);
    }

    public RoleAdherenceMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public RoleAdherenceMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public RoleAdherenceMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Role Adherence threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name(), true);
        outOfCharacterVerdicts = extractOutOfCharacterVerdicts(testCase.turns(), testCase.chatbotRole());
        score = calculateScore(testCase.turns());
        reason = includeReason ? generateReason(testCase.chatbotRole()).reason() : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Role Adherence";
    }

    public OutOfCharacterResponseVerdicts outOfCharacterVerdicts() {
        return outOfCharacterVerdicts;
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

    protected OutOfCharacterResponseVerdicts extractOutOfCharacterVerdicts(List<Turn> turns, String role) {
        requireModel();
        var parsed = RoleAdherenceSchemas.parseVerdicts(
                model.generate(RoleAdherencePrompts.extractOutOfCharacterVerdicts(turns, role)));
        return new OutOfCharacterResponseVerdicts(parsed.verdicts().stream()
                .map(verdict -> withAiMessage(verdict, turns))
                .toList());
    }

    protected RoleAdherenceScoreReason generateReason(String role) {
        requireModel();
        var responses = outOfCharacterVerdicts.verdicts().stream()
                .map(OutOfCharacterResponseVerdict::aiMessage)
                .toList();
        return RoleAdherenceSchemas.parseReason(
                model.generate(RoleAdherencePrompts.generateReason(score, role, responses)));
    }

    private OutOfCharacterResponseVerdict withAiMessage(OutOfCharacterResponseVerdict verdict, List<Turn> turns) {
        if (verdict.index() < 0 || verdict.index() >= turns.size()) {
            return verdict;
        }
        return new OutOfCharacterResponseVerdict(
                verdict.index(), verdict.reason(), turns.get(verdict.index()).content() + " (turn #" + (verdict.index() + 1) + ")");
    }

    private double calculateScore(List<Turn> turns) {
        var assistantTurns = turns.stream().filter(turn -> "assistant".equals(turn.role())).count();
        if (assistantTurns == 0) {
            return 1.0;
        }
        var rawScore = (assistantTurns - Math.min(outOfCharacterVerdicts.verdicts().size(), assistantTurns))
                / (double) assistantTurns;
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Role Adherence generation requires a model provider");
        }
    }
}
