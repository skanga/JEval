package dev.jeval.synthesizer;

public record ContextConstructionConfig(
        int maxContextsPerDocument,
        int minContextsPerDocument,
        int chunkSize,
        int chunkOverlap,
        double contextQualityThreshold,
        double contextSimilarityThreshold,
        int maxRetries,
        boolean allowCrossFileContexts,
        Integer targetFilesPerContext,
        int maxFilesPerContext) {
    public static final ContextConstructionConfig DEFAULT =
            new ContextConstructionConfig(3, 1, 1024, 0, 0.5, 0.0, 3, false, null, 3);

    public ContextConstructionConfig(
            int maxContextsPerDocument,
            int minContextsPerDocument,
            int chunkSize,
            int chunkOverlap,
            double contextQualityThreshold,
            double contextSimilarityThreshold,
            int maxRetries) {
        this(maxContextsPerDocument, minContextsPerDocument, chunkSize, chunkOverlap,
                contextQualityThreshold, contextSimilarityThreshold, maxRetries, false, null, 3);
    }

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
        if (contextQualityThreshold < 0 || contextQualityThreshold > 1) {
            throw new IllegalArgumentException("context_quality_threshold must be between 0 and 1.");
        }
        if (contextSimilarityThreshold < 0 || contextSimilarityThreshold > 1) {
            throw new IllegalArgumentException("context_similarity_threshold must be between 0 and 1.");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("max_retries must be non-negative");
        }
        if (targetFilesPerContext != null && targetFilesPerContext < 2) {
            throw new IllegalArgumentException("target_files_per_context must be at least 2 when provided.");
        }
        if (maxFilesPerContext < 2) {
            throw new IllegalArgumentException("max_files_per_context must be at least 2.");
        }
    }
}
