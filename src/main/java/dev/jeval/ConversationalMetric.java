package dev.jeval;

import java.util.concurrent.CompletableFuture;

public interface ConversationalMetric {
    MetricResult measure(ConversationalTestCase testCase);

    default CompletableFuture<MetricResult> aMeasure(ConversationalTestCase testCase) {
        return CompletableFuture.supplyAsync(() -> measure(testCase));
    }
}
