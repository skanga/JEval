package dev.jeval.metrics;

import dev.jeval.SingleTurnParam;
import java.util.List;

public final class RAGASAnswerRelevancyMetric extends AbstractRagasMetric {
    public RAGASAnswerRelevancyMetric() {
        this(0.3, unavailableScorer());
    }

    public RAGASAnswerRelevancyMetric(double threshold, RagasScorer scorer) {
        super(threshold, scorer, List.of(
                SingleTurnParam.INPUT,
                SingleTurnParam.ACTUAL_OUTPUT,
                SingleTurnParam.RETRIEVAL_CONTEXT));
    }

    @Override
    public String metricKey() {
        return "answer_relevancy";
    }

    @Override
    public String baseName() {
        return "Answer Relevancy";
    }
}
