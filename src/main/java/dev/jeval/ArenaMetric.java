package dev.jeval;

import java.util.concurrent.CompletableFuture;

public interface ArenaMetric {
    String measure(ArenaTestCase testCase);

    default CompletableFuture<String> aMeasure(ArenaTestCase testCase) {
        return CompletableFuture.supplyAsync(() -> measure(testCase));
    }

    String name();

    String reason();

    boolean success();
}
