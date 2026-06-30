package dev.jeval.synthesizer;

import java.nio.charset.Charset;

public record ContextConstructionConfig(
        int maxContextsPerDocument,
        int minContextsPerDocument,
        int maxContextLength,
        int minContextLength,
        int chunkSize,
        int chunkOverlap,
        double contextQualityThreshold,
        double contextSimilarityThreshold,
        int maxRetries,
        boolean allowCrossFileContexts,
        Integer targetFilesPerContext,
        int maxFilesPerContext,
        String encoding) {
    public static final ContextConstructionConfig DEFAULT =
            new ContextConstructionConfig(3, 1, 3, 1, 1024, 0, 0.5, 0.0, 3, false, null, 3, null);

    public ContextConstructionConfig(
            int maxContextsPerDocument,
            int minContextsPerDocument,
            int chunkSize,
            int chunkOverlap,
            double contextQualityThreshold,
            double contextSimilarityThreshold,
            int maxRetries) {
        this(maxContextsPerDocument, minContextsPerDocument, 1, 1, chunkSize, chunkOverlap,
                contextQualityThreshold, contextSimilarityThreshold, maxRetries, false, null, 3, null);
    }

    public ContextConstructionConfig(
            int maxContextsPerDocument,
            int minContextsPerDocument,
            int maxContextLength,
            int minContextLength,
            int chunkSize,
            int chunkOverlap,
            double contextQualityThreshold,
            double contextSimilarityThreshold,
            int maxRetries) {
        this(maxContextsPerDocument, minContextsPerDocument, maxContextLength, minContextLength, chunkSize, chunkOverlap,
                contextQualityThreshold, contextSimilarityThreshold, maxRetries, false, null, 3, null);
    }

    public ContextConstructionConfig(
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
        this(maxContextsPerDocument, minContextsPerDocument, 1, 1, chunkSize, chunkOverlap,
                contextQualityThreshold, contextSimilarityThreshold, maxRetries,
                allowCrossFileContexts, targetFilesPerContext, maxFilesPerContext, null);
    }

    public ContextConstructionConfig(
            int maxContextsPerDocument,
            int minContextsPerDocument,
            int maxContextLength,
            int minContextLength,
            int chunkSize,
            int chunkOverlap,
            double contextQualityThreshold,
            double contextSimilarityThreshold,
            int maxRetries,
            boolean allowCrossFileContexts,
            Integer targetFilesPerContext,
            int maxFilesPerContext) {
        this(maxContextsPerDocument, minContextsPerDocument, maxContextLength, minContextLength, chunkSize, chunkOverlap,
                contextQualityThreshold, contextSimilarityThreshold, maxRetries,
                allowCrossFileContexts, targetFilesPerContext, maxFilesPerContext, null);
    }

    public ContextConstructionConfig {
        if (maxContextsPerDocument < 1) {
            throw new IllegalArgumentException("max_contexts_per_document must be at least 1");
        }
        if (minContextsPerDocument < 0) {
            throw new IllegalArgumentException("min_contexts_per_document must be non-negative");
        }
        if (maxContextLength < 1) {
            throw new IllegalArgumentException("max_context_length must be at least 1");
        }
        if (minContextLength < 1) {
            throw new IllegalArgumentException("min_context_length must be at least 1");
        }
        if (minContextLength > maxContextLength) {
            throw new IllegalArgumentException("min_context_length must not exceed max_context_length.");
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
        if (encoding != null && !encoding.isBlank()) {
            try {
                Charset.forName(encoding);
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Unsupported encoding: " + encoding, error);
            }
        }
        encoding = encoding == null || encoding.isBlank() ? null : encoding;
    }
}
