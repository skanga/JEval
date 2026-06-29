package dev.jeval.synthesizer;

public record ContextConstructionConfig(
        int maxContextsPerDocument,
        int minContextsPerDocument,
        int chunkSize,
        int chunkOverlap,
        double contextQualityThreshold,
        double contextSimilarityThreshold,
        int maxRetries) {
    public static final ContextConstructionConfig DEFAULT =
            new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3);

    public ContextConstructionConfig {
        if (maxContextsPerDocument < 1) {
            throw new IllegalArgumentException("max_contexts_per_document must be at least 1");
        }
        if (minContextsPerDocument < 0) {
            throw new IllegalArgumentException("min_contexts_per_document must be non-negative");
        }
        if (chunkSize < 1) {
            throw new IllegalArgumentException("chunk_size must be at least 1");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunk_overlap must be non-negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max_retries must be non-negative");
        }
    }
}
