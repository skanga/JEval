package dev.jeval.metrics;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class RAGASContextualEntitiesRecall extends AbstractRagasMetric {
    public RAGASContextualEntitiesRecall() {
        this(0.3, unavailableScorer());
    }

    public RAGASContextualEntitiesRecall(double threshold, RagasScorer scorer) {
        super(threshold, scorer, List.of(
                SingleTurnParam.EXPECTED_OUTPUT,
                SingleTurnParam.RETRIEVAL_CONTEXT));
    }

    @Override
    public String metricKey() {
        return "context_entity_recall";
    }

    @Override
    public String baseName() {
        return "Contextual Entities Recall";
    }
}
