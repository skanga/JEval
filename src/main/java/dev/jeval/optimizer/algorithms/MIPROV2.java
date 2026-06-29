package dev.jeval.optimizer.algorithms;

import dev.jeval.optimizer.AcceptedIteration;
import dev.jeval.optimizer.OptimizationReport;
import dev.jeval.optimizer.OptimizationResult;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.PromptOptimizationAlgorithm;
import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        var paretoScores = new LinkedHashMap<String, List<Double>>();
        var parents = new LinkedHashMap<String, String>();
        parents.put(rootConfig.id(), null);
        var promptConfigurations = new LinkedHashMap<String, PromptConfiguration>();
        promptConfigurations.put(rootConfig.id(), rootConfig);
        var acceptedIterations = new ArrayList<AcceptedIteration>();

        var candidates = proposeInstructionCandidates(prompt, goldens, scorer);
        var demoSets = bootstrapDemonstrationSets(prompt, goldens, scorer);
        var configurationsByPair = new LinkedHashMap<PairKey, PromptConfiguration>();

        var rootScores = scorer.scorePareto(rootConfig, goldens);
        paretoScores.put(rootConfig.id(), rootScores);
        var bestConfig = rootConfig;
        var bestScore = average(rootScores);

        var trialPairs = trialPairs(candidates.size(), demoSets.size());
        var trials = Math.min(numTrials, trialPairs.size());
        for (var trialIndex = 0; trialIndex < trials; trialIndex++) {
            var pair = trialPairs.get(trialIndex);
            var candidateConfig = configurationsByPair.computeIfAbsent(pair, key -> {
                var config = promptConfiguration(candidates.get(key.instructionIndex()), demoSets.get(key.demoIndex()));
                promptConfigurations.put(config.id(), config);
                parents.put(config.id(), rootConfig.id());
                return config;
            });

            var minibatch = sampleMinibatch(goldens);
            var minibatchScore = scorer.scoreMinibatch(candidateConfig, minibatch);
            var shouldFullEvaluate = trialIndex == trials - 1
                    || (minibatchFullEvalSteps > 0 && (trialIndex + 1) % minibatchFullEvalSteps == 0);
            if (shouldFullEvaluate || minibatchScore > bestScore) {
                var fullScores = scorer.scorePareto(candidateConfig, goldens);
                paretoScores.put(candidateConfig.id(), fullScores);
                var fullScore = average(fullScores);
                if (fullScore > bestScore) {
                    acceptedIterations.add(new AcceptedIteration(
                            bestConfig.id(),
                            candidateConfig.id(),
                            OptimizerScorer.DEFAULT_MODULE_ID,
                            bestScore,
                            fullScore));
                    bestConfig = candidateConfig;
                    bestScore = fullScore;
                }
            }
        }

        var report = new OptimizationReport(
                optimizationId,
                bestConfig.id(),
                acceptedIterations,
                paretoScores,
                parents,
                OptimizerUtils.buildPromptConfigSnapshots(promptConfigurations));
        return new OptimizationResult(bestConfig.prompts().get(OptimizerScorer.DEFAULT_MODULE_ID), report);
    }

    private List<Prompt> proposeInstructionCandidates(Prompt prompt, List<?> goldens, OptimizerScorer scorer) {
        if (numCandidates <= 1) {
            return List.of(prompt);
        }
        var proposer = new MIPROV2InstructionProposer(template -> scorer.generate(
                Map.of(OptimizerScorer.DEFAULT_MODULE_ID, new Prompt("miprov2-proposer", template)),
                null), randomState);
        return proposer.propose(prompt, goldens, numCandidates);
    }

    private List<MIPROV2DemonstrationSet> bootstrapDemonstrationSets(
            Prompt prompt,
            List<?> goldens,
            OptimizerScorer scorer) {
        if (maxBootstrappedDemonstrations == 0 && maxLabeledDemonstrations == 0) {
            return List.of(new MIPROV2DemonstrationSet(List.of(), "0-shot"));
        }
        var bootstrapper = new MIPROV2Bootstrapper(
                scorer,
                maxBootstrappedDemonstrations,
                maxLabeledDemonstrations,
                numDemonstrationSets,
                randomState);
        return bootstrapper.bootstrap(prompt, goldens);
    }

    private PromptConfiguration promptConfiguration(Prompt candidate, MIPROV2DemonstrationSet demoSet) {
        var promptWithDemos = MIPROV2Bootstrapper.renderPromptWithDemonstrations(candidate, demoSet, 8);
        return PromptConfiguration.create(Map.of(OptimizerScorer.DEFAULT_MODULE_ID, promptWithDemos));
    }

    private List<?> sampleMinibatch(List<?> goldens) {
        if (goldens.size() <= minibatchSize) {
            return goldens;
        }
        var shuffled = new ArrayList<>(goldens);
        java.util.Collections.shuffle(shuffled, randomState);
        return List.copyOf(shuffled.subList(0, minibatchSize));
    }

    private static List<PairKey> trialPairs(int candidateCount, int demoSetCount) {
        var pairs = new ArrayList<PairKey>();
        for (var instructionIndex = 0; instructionIndex < candidateCount; instructionIndex++) {
            for (var demoIndex = 0; demoIndex < demoSetCount; demoIndex++) {
                pairs.add(new PairKey(instructionIndex, demoIndex));
            }
        }
        return pairs;
    }

    private static double average(List<Double> scores) {
        return scores.isEmpty() ? 0.0 : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private record PairKey(int instructionIndex, int demoIndex) {
    }
}
