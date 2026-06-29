package dev.jeval.optimizer.algorithms;

import dev.jeval.optimizer.OptimizationReport;
import dev.jeval.optimizer.OptimizationResult;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.PromptOptimizationAlgorithm;
import dev.jeval.prompt.Prompt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class COPRO implements PromptOptimizationAlgorithm {
    private final int depth;
    private final int breadth;
    private final int minibatchSize;
    private final int seed;
    private final Random randomState;

    public COPRO() {
        this(4, 7, 25, (Random) null);
    }

    public COPRO(int randomState) {
        this(4, 7, 25, randomState);
    }

    public COPRO(Random randomState) {
        this(4, 7, 25, randomState);
    }

    public COPRO(int depth, int breadth, int minibatchSize, int randomState) {
        this(depth, breadth, minibatchSize, randomState, new Random(randomState));
    }

    public COPRO(int depth, int breadth, int minibatchSize, Random randomState) {
        this(depth, breadth, minibatchSize, new Random().nextInt(1_000_000), randomState);
    }

    private COPRO(int depth, int breadth, int minibatchSize, int seed, Random randomState) {
        this.depth = depth;
        this.breadth = breadth;
        this.minibatchSize = minibatchSize;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
    }

    public int depth() {
        return depth;
    }

    public int breadth() {
        return breadth;
    }

    public int minibatchSize() {
        return minibatchSize;
    }

    public int seed() {
        return seed;
    }

    public Random randomState() {
        return randomState;
    }

    @Override
    public OptimizationResult execute(Prompt prompt, List<?> goldens, OptimizerScorer scorer) {
        var optimizationId = UUID.randomUUID().toString();
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put(OptimizerScorer.DEFAULT_MODULE_ID, prompt);
        var rootConfig = PromptConfiguration.create(prompts);
        var scores = scorer.scorePareto(rootConfig, goldens);

        var paretoScores = new LinkedHashMap<String, List<Double>>();
        paretoScores.put(rootConfig.id(), scores);
        var parents = new LinkedHashMap<String, String>();
        parents.put(rootConfig.id(), null);
        var promptConfigurations = new LinkedHashMap<String, PromptConfiguration>();
        promptConfigurations.put(rootConfig.id(), rootConfig);

        var report = new OptimizationReport(
                optimizationId,
                rootConfig.id(),
                List.of(),
                paretoScores,
                parents,
                OptimizerUtils.buildPromptConfigSnapshots(promptConfigurations));
        return new OptimizationResult(prompt, report);
    }
}
