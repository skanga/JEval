package dev.jeval.optimizer.policies;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class OptimizerPolicies {
    private OptimizerPolicies() {
    }

    public static TieBreakResult pickBestWithTies(
            Map<String, Double> totals,
            Map<String, String> parentsById,
            Random randomState) {
        return pickBestWithTies(totals, parentsById, randomState, 1e-9, TieBreaker.PREFER_ROOT);
    }

    public static TieBreakResult pickBestWithTies(
            Map<String, Double> totals,
            Map<String, String> parentsById,
            Random randomState,
            double tieTolerance,
            TieBreaker policy) {
        if (totals == null || totals.isEmpty()) {
            throw new IllegalArgumentException("No candidate prompt configuration to choose from.");
        }
        var maxScore = totals.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        var tied = new ArrayList<String>();
        for (var entry : totals.entrySet()) {
            if (Math.abs(entry.getValue() - maxScore) <= tieTolerance) {
                tied.add(entry.getKey());
            }
        }
        if (tied.size() == 1) {
            return new TieBreakResult(tied.getFirst(), List.copyOf(tied), maxScore);
        }
        if (policy == TieBreaker.PREFER_CHILD) {
            for (var i = totals.size() - 1; i >= 0; i--) {
                var id = totals.keySet().stream().toList().get(i);
                if (tied.contains(id) && parentsById.get(id) != null) {
                    return new TieBreakResult(id, List.copyOf(tied), maxScore);
                }
            }
        }
        if (policy == TieBreaker.RANDOM) {
            return new TieBreakResult(tied.get(randomState.nextInt(tied.size())), List.copyOf(tied), maxScore);
        }
        for (var id : tied) {
            if (parentsById.get(id) == null) {
                return new TieBreakResult(id, List.copyOf(tied), maxScore);
            }
        }
        return new TieBreakResult(tied.getFirst(), List.copyOf(tied), maxScore);
    }

    public static boolean isDominated(List<Double> candidateScores, List<Double> otherScores, double minDelta) {
        var otherGeEverywhere = true;
        var otherGtSomewhere = false;
        for (var i = 0; i < Math.min(candidateScores.size(), otherScores.size()); i++) {
            var candidate = candidateScores.get(i);
            var other = otherScores.get(i);
            if (other + 1e-9 < candidate) {
                otherGeEverywhere = false;
            }
            if (other > candidate + minDelta) {
                otherGtSomewhere = true;
            }
        }
        return otherGeEverywhere && otherGtSomewhere;
    }

    public static List<String> paretoFrontier(List<String> promptConfigurationIds,
            Map<String, List<Double>> scoreTable) {
        var frontier = new ArrayList<String>();
        for (var id : promptConfigurationIds) {
            var candidate = scoreTable.get(id);
            var dominated = false;
            for (var frontierId : frontier) {
                if (isDominated(candidate, scoreTable.get(frontierId), 0.01)) {
                    dominated = true;
                    break;
                }
            }
            if (dominated) {
                continue;
            }
            frontier.removeIf(frontierId -> isDominated(scoreTable.get(frontierId), candidate, 0.01));
            frontier.add(id);
        }
        return List.copyOf(frontier);
    }

    public static Map<String, Integer> frequencyWeights(Map<String, List<Double>> scoreTable) {
        if (scoreTable == null || scoreTable.isEmpty()) {
            return Map.of();
        }
        var instanceCount = scoreTable.values().iterator().next().size();
        var allCandidates = new ArrayList<>(scoreTable.keySet());
        var perInstanceFrontiers = new ArrayList<List<String>>();
        for (var i = 0; i < instanceCount; i++) {
            var index = i;
            var bestScore = allCandidates.stream()
                    .mapToDouble(id -> scoreTable.get(id).get(index))
                    .max()
                    .orElseThrow();
            var winners = allCandidates.stream()
                    .filter(id -> scoreTable.get(id).get(index) == bestScore)
                    .toList();
            var oneDimensionalScores = new LinkedHashMap<String, List<Double>>();
            for (var winner : winners) {
                oneDimensionalScores.put(winner, List.of(scoreTable.get(winner).get(index)));
            }
            perInstanceFrontiers.add(paretoFrontier(winners, oneDimensionalScores));
        }
        var candidateUnion = perInstanceFrontiers.stream()
                .flatMap(List::stream)
                .distinct()
                .sorted()
                .toList();
        var globalFrontier = paretoFrontier(candidateUnion, scoreTable);
        var weights = new LinkedHashMap<String, Integer>();
        for (var id : globalFrontier) {
            weights.put(id, 0);
        }
        for (var winners : perInstanceFrontiers) {
            for (var id : winners) {
                if (weights.containsKey(id)) {
                    weights.put(id, weights.get(id) + 1);
                }
            }
        }
        return Map.copyOf(weights);
    }

    public static String sampleByFrequency(Map<String, Integer> frequencyByPromptConfig, Random randomState) {
        if (frequencyByPromptConfig == null || frequencyByPromptConfig.isEmpty()) {
            throw new IllegalArgumentException("No prompt configurations to sample.");
        }
        var items = new ArrayList<>(frequencyByPromptConfig.entrySet());
        var totalWeight = items.stream().mapToInt(Map.Entry::getValue).sum();
        if (totalWeight == 0) {
            return items.get(randomState.nextInt(items.size())).getKey();
        }
        var r = randomState.nextDouble(totalWeight);
        var cumulative = 0.0;
        for (var item : items) {
            cumulative += item.getValue();
            if (r <= cumulative) {
                return item.getKey();
            }
        }
        return items.getLast().getKey();
    }

    public static String selectPromptConfigurationPareto(Map<String, List<Double>> scoreTable, Random randomState) {
        return sampleByFrequency(frequencyWeights(scoreTable), randomState);
    }

    public record TieBreakResult(String chosenId, List<String> tiedIds, double maxScore) {
    }
}
