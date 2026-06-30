package dev.jeval.metrics;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class RAGASContextualPrecisionMetric extends AbstractRagasMetric {
    public RAGASContextualPrecisionMetric() {
        this(0.3, unavailableScorer());
    }

    public RAGASContextualPrecisionMetric(double threshold, RagasScorer scorer) {
        super(threshold, scorer, List.of(
                SingleTurnParam.INPUT,
                SingleTurnParam.EXPECTED_OUTPUT,
                SingleTurnParam.RETRIEVAL_CONTEXT));
    }

    @Override
    public String metricKey() {
        return "context_precision";
    }

    @Override
    public String baseName() {
        return "Contextual Precision";
    }
}
