package dev.jeval.benchmarks;

public record BenchmarkResult(double overallAccuracy) {
    public BenchmarkResult {
        if (!Double.isFinite(overallAccuracy)) {
            throw new IllegalArgumentException("BenchmarkResult overallAccuracy must be finite");
        }
    }
}
