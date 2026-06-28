package dev.jeval;

public interface Metric {
    MetricResult measure(LlmTestCase testCase);
}
