package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;
import java.util.List;
import java.util.Objects;

public final class PromptOptimizer {
    private final ModelCallback modelCallback;
    private final List<?> metrics;
    private final PromptOptimizationAlgorithm algorithm;
    private OptimizationReport optimizationReport;

    public PromptOptimizer(ModelCallback modelCallback, List<?> metrics, PromptOptimizationAlgorithm algorithm) {
        this.modelCallback = (ModelCallback) OptimizerUtils.validateCallback("PromptOptimizer", modelCallback);
        this.metrics = OptimizerUtils.validateMetrics("PromptOptimizer", metrics);
        this.algorithm = Objects.requireNonNull(algorithm, "algorithm");
    }

    public Prompt optimize(Prompt prompt, List<?> goldens) {
        var result = algorithm.execute(prompt, goldens, new OptimizerScorer(modelCallback, metrics));
        optimizationReport = result.report();
        return result.prompt();
    }

    public OptimizationReport optimizationReport() {
        return optimizationReport;
    }
}
