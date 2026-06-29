package dev.jeval.optimizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record OptimizationReport(
        String optimizationId,
        String bestId,
        List<AcceptedIteration> acceptedIterations,
        Map<String, List<Double>> paretoScores,
        Map<String, String> parents,
        Map<String, PromptConfigSnapshot> promptConfigurations) {

    public OptimizationReport {
        acceptedIterations = List.copyOf(Objects.requireNonNull(acceptedIterations, "acceptedIterations"));
        paretoScores = copyScoreTable(paretoScores);
        parents = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(parents, "parents")));
        promptConfigurations = Collections.unmodifiableMap(
                new LinkedHashMap<>(Objects.requireNonNull(promptConfigurations, "promptConfigurations")));
    }

    private static Map<String, List<Double>> copyScoreTable(Map<String, List<Double>> source) {
        var copy = new LinkedHashMap<String, List<Double>>();
        for (var entry : Objects.requireNonNull(source, "paretoScores").entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
