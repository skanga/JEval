package dev.jeval.optimizer;

import dev.jeval.ConversationalMetric;
import dev.jeval.DeepEvalException;
import dev.jeval.Metric;
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

    public static Object validateInstance(
            String component,
            String paramName,
            Object value,
            boolean allowNull,
            Class<?>... expectedTypes) {
        if (value == null && allowNull) {
            return null;
        }
        for (var expectedType : expectedTypes) {
            if (expectedType.isInstance(value)) {
                return value;
            }
        }
        throw new DeepEvalException(component + " expected `" + paramName + "` to be an instance of "
                + formatTypeNames(expectedTypes) + ", but received "
                + (value == null ? "null" : value.getClass().getSimpleName()) + " instead.");
    }

    public static List<?> validateSequenceOf(
            String component,
            String paramName,
            Object value,
            boolean allowNull,
            Class<?>... expectedItemTypes) {
        if (value == null && allowNull) {
            return null;
        }
        if (!(value instanceof List<?> list)) {
            throw new DeepEvalException(component + " expected `" + paramName + "` to be a List of "
                    + formatTypeNames(expectedItemTypes) + ", but received "
                    + (value == null ? "null" : value.getClass().getSimpleName()) + " instead.");
        }
        for (var i = 0; i < list.size(); i++) {
            var item = list.get(i);
            if (!matchesAny(item, expectedItemTypes)) {
                throw new DeepEvalException(component + " expected all elements of `" + paramName
                        + "` to be instances of " + formatTypeNames(expectedItemTypes)
                        + ", but element at index " + i + " has type "
                        + (item == null ? "null" : item.getClass().getSimpleName()) + ".");
            }
        }
        return list;
    }

    public static Object validateCallback(String component, Object modelCallback) {
        if (modelCallback == null) {
            throw new DeepEvalException(component + " requires a `model_callback`.\n\n"
                    + "Supply a custom callable via `modelCallback` that performs generation and returns the model output.");
        }
        return modelCallback;
    }

    public static String invokeModelCallback(ModelCallback modelCallback, dev.jeval.prompt.Prompt prompt, Object golden) {
        return modelCallback.generate(prompt, golden);
    }

    public static List<?> validateMetrics(String component, Object metrics) {
        if (!(metrics instanceof List<?> list) || list.isEmpty()) {
            throw new DeepEvalException(component + " requires a `metrics`.\n\n"
                    + "Supply one or more DeepEval metrics via `metrics`.");
        }
        validateSequenceOf(component, "metrics", list, false, Metric.class, ConversationalMetric.class);
        return List.copyOf(list);
    }

    public static int validateIntInRange(
            String component,
            String paramName,
            int value,
            Integer minInclusive,
            Integer maxExclusive) {
        if (minInclusive != null && value < minInclusive) {
            throw rangeError(component, paramName, value, minInclusive, maxExclusive);
        }
        if (maxExclusive != null && value >= maxExclusive) {
            throw rangeError(component, paramName, value, minInclusive, maxExclusive);
        }
        return value;
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

    private static boolean matchesAny(Object value, Class<?>[] expectedTypes) {
        for (var expectedType : expectedTypes) {
            if (expectedType.isInstance(value)) {
                return true;
            }
        }
        return false;
    }

    private static DeepEvalException rangeError(
            String component,
            String paramName,
            int value,
            Integer minInclusive,
            Integer maxExclusive) {
        if (minInclusive != null && maxExclusive != null) {
            return new DeepEvalException(component + " expected `" + paramName + "` to be between "
                    + minInclusive + " and " + (maxExclusive - 1) + " (inclusive), but received " + value
                    + " instead.");
        }
        if (minInclusive != null) {
            return new DeepEvalException(component + " expected `" + paramName + "` to be >= "
                    + minInclusive + ", but received " + value + " instead.");
        }
        return new DeepEvalException(component + " expected `" + paramName + "` to be < "
                + maxExclusive + ", but received " + value + " instead.");
    }

    private static String formatTypeNames(Class<?>[] types) {
        var names = new ArrayList<String>();
        for (var type : types) {
            names.add(type.getSimpleName());
        }
        if (names.size() == 1) {
            return names.getFirst();
        }
        if (names.size() == 2) {
            return names.get(0) + " or " + names.get(1);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + ", or " + names.getLast();
    }
}
