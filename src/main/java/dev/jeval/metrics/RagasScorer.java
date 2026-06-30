package dev.jeval.metrics;

import dev.jeval.LlmTestCase;

@FunctionalInterface
public interface RagasScorer {
    double score(String metricKey, LlmTestCase testCase);
}
