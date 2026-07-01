package dev.jeval.benchmarks;

public record BenchmarkTaskScore(String task, double score) {
    public BenchmarkTaskScore {
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("BenchmarkTaskScore score must be finite");
        }
    }
}
