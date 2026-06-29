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

public final class MIPROV2 implements PromptOptimizationAlgorithm {
    private final int numTrials;
    private final int numCandidates;
    private final int maxBootstrappedDemonstrations;
    private final int maxLabeledDemonstrations;
    private final int numDemonstrationSets;
    private final int minibatchSize;
    private final int minibatchFullEvalSteps;
    private final int seed;
    private final Random randomState;

    public MIPROV2() {
        this(30, 10, 4, 4, 5, 25, 10, (Random) null);
    }

    public MIPROV2(int randomState) {
        this(30, 10, 4, 4, 5, 25, 10, randomState);
    }

    public MIPROV2(Random randomState) {
        this(30, 10, 4, 4, 5, 25, 10, randomState);
    }

    public MIPROV2(
            int numTrials,
            int numCandidates,
            int maxBootstrappedDemonstrations,
            int maxLabeledDemonstrations,
            int numDemonstrationSets,
            int minibatchSize,
            int minibatchFullEvalSteps,
            int randomState) {
        this(
                numTrials,
                numCandidates,
                maxBootstrappedDemonstrations,
                maxLabeledDemonstrations,
                numDemonstrationSets,
                minibatchSize,
                minibatchFullEvalSteps,
                randomState,
                new Random(randomState));
    }

    public MIPROV2(
            int numTrials,
            int numCandidates,
            int maxBootstrappedDemonstrations,
            int maxLabeledDemonstrations,
            int numDemonstrationSets,
            int minibatchSize,
            int minibatchFullEvalSteps,
            Random randomState) {
        this(
                numTrials,
                numCandidates,
                maxBootstrappedDemonstrations,
                maxLabeledDemonstrations,
                numDemonstrationSets,
                minibatchSize,
                minibatchFullEvalSteps,
                new Random().nextInt(1_000_000),
                randomState);
    }

    private MIPROV2(
            int numTrials,
            int numCandidates,
            int maxBootstrappedDemonstrations,
            int maxLabeledDemonstrations,
            int numDemonstrationSets,
            int minibatchSize,
            int minibatchFullEvalSteps,
            int seed,
            Random randomState) {
        this.numTrials = numTrials;
        this.numCandidates = numCandidates;
        this.maxBootstrappedDemonstrations = maxBootstrappedDemonstrations;
        this.maxLabeledDemonstrations = maxLabeledDemonstrations;
        this.numDemonstrationSets = numDemonstrationSets;
        this.minibatchSize = minibatchSize;
        this.minibatchFullEvalSteps = minibatchFullEvalSteps;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
    }

    public int numTrials() {
        return numTrials;
    }

    public int numCandidates() {
        return numCandidates;
    }

    public int maxBootstrappedDemonstrations() {
        return maxBootstrappedDemonstrations;
    }

    public int maxLabeledDemonstrations() {
        return maxLabeledDemonstrations;
    }

    public int numDemonstrationSets() {
        return numDemonstrationSets;
    }

    public int minibatchSize() {
        return minibatchSize;
    }

    public int minibatchFullEvalSteps() {
        return minibatchFullEvalSteps;
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
