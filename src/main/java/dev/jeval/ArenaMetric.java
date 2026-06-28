package dev.jeval;

public interface ArenaMetric {
    String measure(ArenaTestCase testCase);

    String name();

    String reason();

    boolean success();
}
