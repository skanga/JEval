package dev.jeval.runner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TraceMetricScores(
        Map<String, Map<String, MetricScores>> agent,
        Map<String, Map<String, MetricScores>> tool,
        Map<String, Map<String, MetricScores>> retriever,
        Map<String, Map<String, MetricScores>> llm,
        Map<String, Map<String, MetricScores>> base) {

    public TraceMetricScores() {
        this(null, null, null, null, null);
    }

    public TraceMetricScores {
        agent = copyNestedScores(agent);
        tool = copyNestedScores(tool);
        retriever = copyNestedScores(retriever);
        llm = copyNestedScores(llm);
        base = copyNestedScores(base);
    }

    private static Map<String, Map<String, MetricScores>> copyNestedScores(
            Map<String, Map<String, MetricScores>> scores) {
        if (scores == null) {
            return Map.of();
        }
        var copied = new LinkedHashMap<String, Map<String, MetricScores>>();
        scores.forEach((span, metricScores) -> copied.put(span, metricScores == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metricScores))));
        return Collections.unmodifiableMap(copied);
    }
}
