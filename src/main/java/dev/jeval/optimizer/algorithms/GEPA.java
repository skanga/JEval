package dev.jeval.optimizer.algorithms;

import dev.jeval.DeepEvalException;
import dev.jeval.optimizer.OptimizationReport;
import dev.jeval.optimizer.OptimizationResult;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.PromptOptimizationAlgorithm;
import dev.jeval.optimizer.policies.TieBreaker;
import dev.jeval.prompt.Prompt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class GEPA implements PromptOptimizationAlgorithm {
    private final int iterations;
    private final int minibatchSize;
    private final int paretoSize;
    private final int randomSeed;
    private final int patience;
    private final TieBreaker tieBreaker;
    private final Random randomState;

    public GEPA() {
        this(5, 8, 3, null, 3, TieBreaker.PREFER_CHILD);
    }

    public GEPA(Integer randomSeed) {
        this(5, 8, 3, randomSeed, 3, TieBreaker.PREFER_CHILD);
    }

    public GEPA(TieBreaker tieBreaker) {
        this(5, 8, 3, null, 3, tieBreaker);
    }

    public GEPA(int iterations, int minibatchSize, int paretoSize, Integer randomSeed, int patience,
            TieBreaker tieBreaker) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations must be >= 1");
        }
        if (minibatchSize < 1) {
            throw new IllegalArgumentException("minibatch_size must be >= 1");
        }
        if (paretoSize < 1) {
            throw new IllegalArgumentException("pareto_size must be >= 1");
        }
        this.iterations = iterations;
        this.minibatchSize = minibatchSize;
        this.paretoSize = paretoSize;
        this.randomSeed = randomSeed == null ? (int) (System.nanoTime() & 0x7fffffff) : randomSeed;
        this.patience = patience;
        this.tieBreaker = tieBreaker == null ? TieBreaker.PREFER_CHILD : tieBreaker;
        this.randomState = new Random(this.randomSeed);
    }

    public int iterations() {
        return iterations;
    }

    public int minibatchSize() {
        return minibatchSize;
    }

    public int paretoSize() {
        return paretoSize;
    }

    public int randomSeed() {
        return randomSeed;
    }

    public int patience() {
        return patience;
    }

    public TieBreaker tieBreaker() {
        return tieBreaker;
    }

    public Random randomState() {
        return randomState;
    }

    @Override
    public OptimizationResult execute(Prompt prompt, List<?> goldens, OptimizerScorer scorer) {
        if (goldens.size() < 2) {
            throw new DeepEvalException("GEPA requires at least 2 goldens to optimize.");
        }
        var optimizationId = UUID.randomUUID().toString();
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put(OptimizerScorer.DEFAULT_MODULE_ID, prompt);
        var rootConfig = PromptConfiguration.create(prompts);
        var split = OptimizerUtils.splitGoldens(goldens, paretoSize, randomState);
        var paretoGoldens = split.pareto();
        var scores = scorer.scorePareto(rootConfig, paretoGoldens);

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
