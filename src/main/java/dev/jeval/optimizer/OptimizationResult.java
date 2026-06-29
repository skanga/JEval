package dev.jeval.optimizer;

import dev.jeval.prompt.Prompt;

public record OptimizationResult(Prompt prompt, OptimizationReport report) {
}
