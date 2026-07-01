package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.ConversationCompletenessSchemas.ConversationCompletenessScoreReason;
import dev.jeval.metrics.ConversationCompletenessSchemas.ConversationCompletenessVerdict;
import dev.jeval.metrics.ConversationCompletenessSchemas.UserIntentions;
import java.util.ArrayList;
import java.util.List;

public class ConversationCompletenessMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<String> userIntentions;
    private List<ConversationCompletenessVerdict> verdicts;
    private double score;
    private String reason;
    private boolean success;

    public ConversationCompletenessMetric() {
        this(0.5, true, false);
    }

    public ConversationCompletenessMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public ConversationCompletenessMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public ConversationCompletenessMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Conversation Completeness threshold must be finite");
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
        userIntentions = extractUserIntentions(testCase.turns(), testCase.multimodal()).intentions();
        verdicts = new ArrayList<>();
        for (var intention : userIntentions) {
            verdicts.add(generateVerdict(testCase.turns(), intention, testCase.multimodal()));
        }
        score = calculateScore();
        reason = includeReason ? generateReason(testCase.multimodal()).reason() : null;
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Conversation Completeness";
    }

    public List<String> userIntentions() {
        return userIntentions;
    }

    public List<ConversationCompletenessVerdict> verdicts() {
        return verdicts;
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

    protected UserIntentions extractUserIntentions(List<Turn> turns, boolean multimodal) {
        requireModel();
        return ConversationCompletenessSchemas.parseUserIntentions(
                model.generate(ConversationCompletenessPrompts.extractUserIntentions(turns, multimodal)));
    }

    protected ConversationCompletenessVerdict generateVerdict(List<Turn> turns, String intention, boolean multimodal) {
        requireModel();
        return ConversationCompletenessSchemas.parseVerdict(
                model.generate(ConversationCompletenessPrompts.generateVerdict(turns, intention, multimodal)));
    }

    protected ConversationCompletenessScoreReason generateReason(boolean multimodal) {
        requireModel();
        var incompletenesses = verdicts.stream()
                .filter(verdict -> verdict != null
                        && verdict.verdict() != null
                        && "no".equalsIgnoreCase(verdict.verdict().strip()))
                .map(ConversationCompletenessVerdict::reason)
                .toList();
        return ConversationCompletenessSchemas.parseReason(
                model.generate(ConversationCompletenessPrompts.generateReason(
                        score, userIntentions, incompletenesses, multimodal)));
    }

    private double calculateScore() {
        var validVerdicts = verdicts.stream()
                .filter(verdict -> verdict != null && verdict.verdict() != null)
                .toList();
        if (validVerdicts.isEmpty()) {
            return 1.0;
        }
        var completeCount = validVerdicts.stream()
                .filter(verdict -> !"no".equalsIgnoreCase(verdict.verdict().strip()))
                .count();
        var rawScore = completeCount / (double) validVerdicts.size();
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Conversation Completeness generation requires a model provider");
        }
    }
}
