package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.DeepEvalException;
import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.prompt.Prompt;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PromptOptimizerTest {

    @Test
    void optimizeDelegatesToAlgorithmAndStoresReport() {
        var original = new Prompt("original", "Original");
        var optimized = new Prompt("optimized", "Optimized");
        var golden = Golden.builder("question").expectedOutput("answer").build();
        var report = new OptimizationReport("opt-1", "best", List.of(), Map.of(), Map.of(), Map.of());
        var algorithm = new CapturingAlgorithm(optimized, report);
        var optimizer = new PromptOptimizer(
                (prompt, callbackGolden) -> ((Golden) callbackGolden).expectedOutput(),
                List.of((Metric) testCase -> new MetricResult("metric", 1.0, 0.5, true, null)),
                algorithm);

        var result = optimizer.optimize(original, List.of(golden));

        assertSame(optimized, result);
        assertSame(report, optimizer.optimizationReport());
        assertSame(original, algorithm.prompt);
        assertEquals(List.of(golden), algorithm.goldens);
        assertEquals(1.0, algorithm.scorer.scoreMinibatch(
                new PromptConfiguration("root", null, Map.of(OptimizerScorer.DEFAULT_MODULE_ID, original)),
                List.of(golden)));
    }

    @Test
    void constructorValidatesCallbackMetricsAndAlgorithm() {
        var metric = List.of((Metric) testCase -> new MetricResult("metric", 1.0, 0.5, true, null));
        var algorithm = new CapturingAlgorithm(new Prompt("optimized", "Optimized"), null);

        assertThrows(DeepEvalException.class, () -> new PromptOptimizer(null, metric, algorithm));
        assertThrows(DeepEvalException.class,
                () -> new PromptOptimizer((prompt, golden) -> "answer", List.of(), algorithm));
        assertThrows(NullPointerException.class,
                () -> new PromptOptimizer((prompt, golden) -> "answer", metric, null));
    }

    private static final class CapturingAlgorithm implements PromptOptimizationAlgorithm {
        private final Prompt optimizedPrompt;
        private final OptimizationReport report;
        private Prompt prompt;
        private List<?> goldens;
        private OptimizerScorer scorer;

        private CapturingAlgorithm(Prompt optimizedPrompt, OptimizationReport report) {
            this.optimizedPrompt = optimizedPrompt;
            this.report = report;
        }

        @Override
        public OptimizationResult execute(Prompt prompt, List<?> goldens, OptimizerScorer scorer) {
            this.prompt = prompt;
            this.goldens = goldens;
            this.scorer = scorer;
            return new OptimizationResult(optimizedPrompt, report);
        }
    }
}
