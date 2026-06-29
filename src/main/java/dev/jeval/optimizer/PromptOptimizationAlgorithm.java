package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;
import java.util.List;

public interface PromptOptimizationAlgorithm {
    OptimizationResult execute(Prompt prompt, List<?> goldens, OptimizerScorer scorer);
}
