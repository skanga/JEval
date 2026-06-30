package dev.jeval.metrics;

import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RagasMetric implements Metric {
    private final double threshold;
    private final RagasScorer scorer;
    private double score;
    private boolean success;
    private Map<String, Double> scoreBreakdown = Map.of();

    public RagasMetric() {
        this(0.3, AbstractRagasMetric.unavailableScorer());
    }

    public RagasMetric(double threshold, RagasScorer scorer) {
        this.threshold = threshold;
        this.scorer = Objects.requireNonNull(scorer, "scorer");
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        var components = List.of(
                new RAGASContextualPrecisionMetric(0.3, scorer),
                new RAGASContextualRecallMetric(0.3, scorer),
                new RAGASContextualEntitiesRecall(0.3, scorer),
                new RAGASAnswerRelevancyMetric(0.3, scorer),
                new RAGASFaithfulnessMetric(0.3, scorer));
        var scores = new LinkedHashMap<String, Double>();
        for (var component : components) {
            var result = component.measure(testCase);
            scores.put(result.name(), result.score());
        }
        scoreBreakdown = Collections.unmodifiableMap(new LinkedHashMap<>(scores));
        score = scores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        success = score >= threshold;
        return new MetricResult(name(), score, threshold, success, null);
    }

    public String name() {
        return "RAGAS";
    }

    public double score() {
        return score;
    }

    public boolean success() {
        return success;
    }

    public Map<String, Double> scoreBreakdown() {
        return scoreBreakdown;
    }
}
