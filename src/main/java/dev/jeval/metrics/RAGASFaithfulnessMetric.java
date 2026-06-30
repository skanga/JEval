package dev.jeval.metrics;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class RAGASFaithfulnessMetric extends AbstractRagasMetric {
    public RAGASFaithfulnessMetric() {
        this(0.3, unavailableScorer());
    }

    public RAGASFaithfulnessMetric(double threshold, RagasScorer scorer) {
        super(threshold, scorer, List.of(
                SingleTurnParam.INPUT,
                SingleTurnParam.ACTUAL_OUTPUT,
                SingleTurnParam.RETRIEVAL_CONTEXT));
    }

    @Override
    public String metricKey() {
        return "faithfulness";
    }

    @Override
    public String baseName() {
        return "Faithfulness";
    }
}
