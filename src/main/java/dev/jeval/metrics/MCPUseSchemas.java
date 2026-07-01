package dev.jeval.metrics;

public final class MCPUseSchemas {
    private MCPUseSchemas() {
    }

    public static MCPPrimitivesScore parsePrimitivesScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new MCPPrimitivesScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public static MCPArgsScore parseArgsScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new MCPArgsScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public record MCPPrimitivesScore(double score, String reason) {
        public MCPPrimitivesScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("MCPPrimitivesScore score must be finite");
            }
        }
    }

    public record MCPArgsScore(double score, String reason) {
        public MCPArgsScore {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("MCPArgsScore score must be finite");
            }
        }
    }
}
