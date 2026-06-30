package dev.jeval;

import java.util.concurrent.CompletableFuture;

public interface Metric {
    MetricResult measure(LlmTestCase testCase);

    default CompletableFuture<MetricResult> aMeasure(LlmTestCase testCase) {
        return CompletableFuture.supplyAsync(() -> measure(testCase));
    }
}
