package dev.jeval.optimizer;

import java.util.List;
import java.util.Objects;

public record ScorerDiagnosisResult(String failures, String successes, String analysis, List<String> results) {
    public ScorerDiagnosisResult {
        failures = failures == null ? "" : failures;
        successes = successes == null ? "" : successes;
        analysis = analysis == null ? "" : analysis;
        results = List.copyOf(Objects.requireNonNull(results, "results"));
    }
}
