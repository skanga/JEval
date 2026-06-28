package dev.jeval.scorer;

import dev.jeval.Utils;
import java.util.ArrayList;
import java.util.List;

public final class Scorer {

    private Scorer() {
    }

    public static int exactMatchScore(String target, String prediction) {
        if (prediction == null || prediction.isEmpty()) {
            return 0;
        }
        return prediction.strip().equals(target.strip()) ? 1 : 0;
    }

    public static int quasiExactMatchScore(String target, String prediction) {
        if (prediction == null || prediction.isEmpty()) {
            return 0;
        }
        return Utils.normalizeText(target).equals(Utils.normalizeText(prediction)) ? 1 : 0;
    }

    public static int quasiContainsScore(List<String> targets, String prediction) {
        if (prediction == null || prediction.isEmpty()) {
            return 0;
        }
        var normalizedPrediction = Utils.normalizeText(prediction);
        for (var target : targets) {
            if (Utils.normalizeText(target).equals(normalizedPrediction)) {
                return 1;
            }
        }
        return 0;
    }

    public static int truthIdentificationScore(String target, String prediction) {
        if (Utils.isMissing(target) || Utils.isMissing(prediction)) {
            return 0;
        }
        try {
            var targets = parseIntegers(target);
            if (targets.isEmpty()) {
                return 0;
            }
            var correct = 0;
            for (var value : parseIntegers(prediction)) {
                if (targets.contains(value)) {
                    correct++;
                }
            }
            return Math.round(correct * 100.0f / targets.size());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static double passAtK(int n, int c, int k) {
        if (n - c < k) {
            return 1.0;
        }
        var product = 1.0;
        for (var i = n - c + 1; i <= n; i++) {
            product *= 1.0 - (double) k / i;
        }
        return 1.0 - product;
    }

    private static List<Integer> parseIntegers(String values) {
        var parsed = new ArrayList<Integer>();
        for (var value : values.replaceAll("^[\\[\\]]+|[\\[\\]]+$", "").split(",")) {
            if (!value.isEmpty()) {
                parsed.add(Integer.parseInt(value.strip()));
            }
        }
        parsed.sort(Integer::compareTo);
        return parsed;
    }
}
