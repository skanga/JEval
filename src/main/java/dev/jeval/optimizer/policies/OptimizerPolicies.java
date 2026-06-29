package dev.jeval.optimizer.policies;

import java.util.ArrayList;
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

    public record TieBreakResult(String chosenId, List<String> tiedIds, double maxScore) {
    }
}
