package dev.jeval.metrics;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class RAGASContextualRecallMetric extends AbstractRagasMetric {
    public RAGASContextualRecallMetric() {
        this(0.3, unavailableScorer());
    }

    public RAGASContextualRecallMetric(double threshold, RagasScorer scorer) {
        super(threshold, scorer, List.of(
                SingleTurnParam.INPUT,
                SingleTurnParam.EXPECTED_OUTPUT,
                SingleTurnParam.RETRIEVAL_CONTEXT));
    }

    @Override
    public String metricKey() {
        return "context_recall";
    }

    @Override
    public String baseName() {
        return "Contextual Recall";
    }
}
