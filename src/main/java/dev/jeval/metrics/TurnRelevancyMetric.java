package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.TurnRelevancySchemas.TurnRelevancyReason;
import dev.jeval.metrics.TurnRelevancySchemas.TurnRelevancyVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TurnRelevancyMetric implements ConversationalMetric {
    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final int windowSize;
    private final double threshold;
    private List<TurnRelevancyVerdict> verdicts;
    private double score;
    private String reason;
    private boolean success;

    public TurnRelevancyMetric() {
        this(0.5, true, false, 10);
    }

    public TurnRelevancyMetric(EvaluationModel model) {
        this(model, 0.5, true, false, 10);
    }

    public TurnRelevancyMetric(double threshold, boolean includeReason, boolean strictMode, int windowSize) {
        this(null, threshold, includeReason, strictMode, windowSize);
    }

    public TurnRelevancyMetric(
            EvaluationModel model,
            double threshold,
            boolean includeReason,
            boolean strictMode,
            int windowSize) {
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.windowSize = Math.max(1, windowSize);
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(
                testCase, List.of(MultiTurnParam.CONTENT, MultiTurnParam.ROLE), name());
        verdicts = new ArrayList<>();
        for (var window : slidingWindows(MetricUtils.getUnitInteractions(testCase.turns()))) {
            verdicts.add(generateVerdict(window));
        }
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason && !verdicts.isEmpty() ? generateReason().reason() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Turn Relevancy";
    }

    public List<TurnRelevancyVerdict> verdicts() {
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

    protected TurnRelevancyVerdict generateVerdict(List<Turn> turnsSlidingWindow) {
        requireModel();
        return TurnRelevancySchemas.parseVerdict(model.generate(TurnRelevancyPrompts.generateVerdict(turnsSlidingWindow)));
    }

    protected TurnRelevancyReason generateReason() {
        requireModel();
        var irrelevancies = new ArrayList<Map<String, String>>();
        for (var i = 0; i < verdicts.size(); i++) {
            var verdict = verdicts.get(i);
            if (verdict != null && verdict.verdict() != null && "no".equalsIgnoreCase(verdict.verdict().strip())) {
                irrelevancies.add(Map.of("message number", String.valueOf(i + 1), "reason", String.valueOf(verdict.reason())));
            }
        }
        return TurnRelevancySchemas.parseReason(model.generate(TurnRelevancyPrompts.generateReason(score, irrelevancies)));
    }

    private double calculateScore() {
        var validVerdicts = verdicts.stream()
                .filter(verdict -> verdict != null && verdict.verdict() != null)
                .toList();
        if (validVerdicts.isEmpty()) {
            return 1.0;
        }
        var relevantCount = validVerdicts.stream()
                .filter(verdict -> !"no".equalsIgnoreCase(verdict.verdict().strip()))
                .count();
        var rawScore = relevantCount / (double) validVerdicts.size();
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private List<List<Turn>> slidingWindows(List<List<Turn>> unitInteractions) {
        var windows = new ArrayList<List<Turn>>();
        for (var i = 0; i < unitInteractions.size(); i++) {
            var flattened = new ArrayList<Turn>();
            for (var unit : unitInteractions.subList(Math.max(0, i - windowSize + 1), i + 1)) {
                flattened.addAll(unit);
            }
            windows.add(List.copyOf(flattened));
        }
        return windows;
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Turn Relevancy generation requires a model provider");
        }
    }
}
