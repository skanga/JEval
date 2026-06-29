package dev.jeval.optimizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class OptimizerUtils {
    private OptimizerUtils() {
    }

    public static Map<String, PromptConfigSnapshot> buildPromptConfigSnapshots(
            Map<String, PromptConfiguration> promptConfigurationsById) {
        var snapshots = new LinkedHashMap<String, PromptConfigSnapshot>();
        for (var entry : Objects.requireNonNull(promptConfigurationsById, "promptConfigurationsById").entrySet()) {
            var config = entry.getValue();
            snapshots.put(entry.getKey(), new PromptConfigSnapshot(config.parent(), config.prompts()));
        }
        return Collections.unmodifiableMap(snapshots);
    }

    public static <T> GoldenSplit<T> splitGoldens(List<T> goldens, int paretoSize, Random randomState) {
        if (paretoSize < 0) {
            throw new IllegalArgumentException("paretoSize must be >= 0");
        }
        Objects.requireNonNull(randomState, "randomState");
        var items = Objects.requireNonNull(goldens, "goldens");
        var total = items.size();
        if (total == 0) {
            return new GoldenSplit<>(List.of(), List.of());
        }
        if (total == 1) {
            return new GoldenSplit<>(List.of(), items);
        }

        var chosenSize = Math.min(paretoSize, total - 1);
        var indices = new ArrayList<Integer>();
        for (var i = 0; i < total; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, randomState);
        var paretoIndices = new HashSet<>(indices.subList(0, chosenSize));

        var feedback = new ArrayList<T>();
        var pareto = new ArrayList<T>();
        for (var i = 0; i < total; i++) {
            if (paretoIndices.contains(i)) {
                pareto.add(items.get(i));
            } else {
                feedback.add(items.get(i));
            }
        }
        return new GoldenSplit<>(feedback, pareto);
    }

    public record GoldenSplit<T>(List<T> feedback, List<T> pareto) {
        public GoldenSplit {
            feedback = List.copyOf(Objects.requireNonNull(feedback, "feedback"));
            pareto = List.copyOf(Objects.requireNonNull(pareto, "pareto"));
        }
    }
}
