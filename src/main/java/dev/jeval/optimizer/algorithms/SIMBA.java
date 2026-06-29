package dev.jeval.optimizer.algorithms;

import dev.jeval.ConversationalGolden;
import dev.jeval.Golden;
import dev.jeval.Turn;
import dev.jeval.optimizer.AcceptedIteration;
import dev.jeval.optimizer.IterationLogEntry;
import dev.jeval.optimizer.OptimizationReport;
import dev.jeval.optimizer.OptimizationResult;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.PromptOptimizationAlgorithm;
import dev.jeval.optimizer.SimbaTraceRecord;
import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

public final class SIMBA implements PromptOptimizationAlgorithm {
    private final int iterations;
    private final int minibatchSize;
    private final int numCandidates;
    private final int numSamples;
    private final int minibatchFullEvalSteps;
    private final int seed;
    private final Random randomState;
    private final Function<String, String> proposeCallback;
    private final List<IterationLogEntry> iterationLog = new ArrayList<>();

    public SIMBA() {
        this(8, 15, 4, 3, 4, (Random) null);
    }

    public SIMBA(int randomState) {
        this(8, 15, 4, 3, 4, randomState);
    }

    public SIMBA(Random randomState) {
        this(8, 15, 4, 3, 4, randomState);
    }

    public SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, int randomState) {
        this(iterations, minibatchSize, numCandidates, numSamples, minibatchFullEvalSteps,
                randomState, new Random(randomState));
    }

    public SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, int randomState, Function<String, String> proposeCallback) {
        this(iterations, minibatchSize, numCandidates, numSamples, minibatchFullEvalSteps,
                randomState, new Random(randomState), proposeCallback);
    }

    public SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, Random randomState) {
        this(iterations, minibatchSize, numCandidates, numSamples, minibatchFullEvalSteps,
                new Random().nextInt(1_000_000), randomState);
    }

    private SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, int seed, Random randomState) {
        this(iterations, minibatchSize, numCandidates, numSamples, minibatchFullEvalSteps, seed, randomState, null);
    }

    private SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, int seed, Random randomState,
            Function<String, String> proposeCallback) {
        this.iterations = iterations;
        this.minibatchSize = minibatchSize;
        this.numCandidates = numCandidates;
        this.numSamples = numSamples;
        this.minibatchFullEvalSteps = minibatchFullEvalSteps;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
        this.proposeCallback = proposeCallback;
    }

    public int iterations() {
        return iterations;
    }

    public int minibatchSize() {
        return minibatchSize;
    }

    public int numCandidates() {
        return numCandidates;
    }

    public int numSamples() {
        return numSamples;
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

    public List<IterationLogEntry> iterationLog() {
        return List.copyOf(iterationLog);
    }

    @Override
    public OptimizationResult execute(Prompt prompt, List<?> goldens, OptimizerScorer scorer) {
        iterationLog.clear();
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
        var acceptedIterations = new ArrayList<AcceptedIteration>();
        var currentBestConfig = rootConfig;
        var globalBestScore = average(scores);

        if (proposeCallback != null) {
            var proposer = new SIMBAProposer(proposeCallback);
            for (var iteration = 1; iteration <= iterations; iteration++) {
                var iterationStart = System.nanoTime();
                var minibatch = sampleMinibatch(goldens);
                var candidateConfigs = candidateConfigs(
                        currentBestConfig,
                        minibatch,
                        scorer,
                        proposer,
                        promptConfigurations);
                if (candidateConfigs.isEmpty()) {
                    iterationLog.add(new IterationLogEntry(
                            iteration,
                            "skipped",
                            "No introspectable variance or ground-truths found.",
                            elapsedSeconds(iterationStart),
                            globalBestScore,
                            globalBestScore));
                    continue;
                }

                var bestBatchConfig = candidateConfigs.stream()
                        .map(config -> new CandidateScore(config, scorer.scoreMinibatch(config, minibatch)))
                        .max(Comparator.comparingDouble(CandidateScore::score))
                        .map(CandidateScore::configuration)
                        .orElseThrow();
                if (iteration == iterations
                        || (minibatchFullEvalSteps > 0 && iteration % minibatchFullEvalSteps == 0)) {
                    var fullScores = scorer.scorePareto(bestBatchConfig, goldens);
                    var fullScore = average(fullScores);
                    paretoScores.put(bestBatchConfig.id(), fullScores);
                    var before = globalBestScore;
                    var accepted = false;
                    if (fullScore > globalBestScore) {
                        acceptedIterations.add(new AcceptedIteration(
                                currentBestConfig.id(),
                                bestBatchConfig.id(),
                                OptimizerScorer.DEFAULT_MODULE_ID,
                                globalBestScore,
                                fullScore));
                        parents.put(bestBatchConfig.id(), currentBestConfig.id());
                        currentBestConfig = bestBatchConfig;
                        globalBestScore = fullScore;
                        accepted = true;
                    }
                    iterationLog.add(new IterationLogEntry(
                            iteration,
                            accepted ? "accepted" : "rejected",
                            "Evaluated on full dataset.",
                            elapsedSeconds(iterationStart),
                            before,
                            fullScore));
                }
            }
        }

        var bestConfig = bestConfig(currentBestConfig, promptConfigurations, paretoScores);
        var report = new OptimizationReport(
                optimizationId,
                bestConfig.id(),
                acceptedIterations,
                paretoScores,
                parents,
                OptimizerUtils.buildPromptConfigSnapshots(promptConfigurations));
        return new OptimizationResult(bestConfig.prompts().get(OptimizerScorer.DEFAULT_MODULE_ID), report);
    }

    private List<?> sampleMinibatch(List<?> goldens) {
        if (goldens.size() <= minibatchSize) {
            return goldens;
        }
        var copy = new ArrayList<>(goldens);
        Collections.shuffle(copy, randomState);
        return List.copyOf(copy.subList(0, minibatchSize));
    }

    private List<PromptConfiguration> candidateConfigs(
            PromptConfiguration currentBestConfig,
            List<?> minibatch,
            OptimizerScorer scorer,
            SIMBAProposer proposer,
            LinkedHashMap<String, PromptConfiguration> promptConfigurations) {
        var candidates = new ArrayList<PromptConfiguration>();
        for (var golden : minibatch) {
            if (candidates.size() >= numCandidates) {
                break;
            }
            var traces = traces(currentBestConfig, golden, scorer);
            if (traces.isEmpty()) {
                continue;
            }
            var bestTrace = traces.getFirst();
            var worstTrace = traces.getLast();
            if (bestTrace.score() < 0.8) {
                var expected = expectedText(golden);
                if (expected == null || expected.isBlank()) {
                    continue;
                }
                bestTrace = new SimbaTraceRecord(
                        expected,
                        1.0,
                        "This is the optimal, ground-truth expected output.");
            } else if (bestTrace.score() == worstTrace.score() && bestTrace.score() >= 0.99) {
                continue;
            }

            var newPrompt = proposer.rewriteFromIntrospection(
                    currentBestConfig.prompts().get(OptimizerScorer.DEFAULT_MODULE_ID),
                    inputs(golden),
                    String.valueOf(bestTrace.output()),
                    bestTrace.score(),
                    bestTrace.feedback(),
                    inputs(golden),
                    String.valueOf(worstTrace.output()),
                    worstTrace.score(),
                    worstTrace.feedback());
            var candidatePrompts = new LinkedHashMap<String, Prompt>();
            candidatePrompts.put(OptimizerScorer.DEFAULT_MODULE_ID, newPrompt);
            var candidate = PromptConfiguration.create(candidatePrompts, currentBestConfig.id());
            promptConfigurations.put(candidate.id(), candidate);
            candidates.add(candidate);
        }
        return List.copyOf(candidates);
    }

    private List<SimbaTraceRecord> traces(
            PromptConfiguration promptConfiguration,
            Object golden,
            OptimizerScorer scorer) {
        var traces = new ArrayList<SimbaTraceRecord>();
        for (var i = 0; i < numSamples; i++) {
            traces.add(scorer.executeTrace(promptConfiguration, golden));
        }
        traces.sort(Comparator.comparingDouble(SimbaTraceRecord::score).reversed());
        return traces;
    }

    private static PromptConfiguration bestConfig(
            PromptConfiguration currentBestConfig,
            LinkedHashMap<String, PromptConfiguration> promptConfigurations,
            LinkedHashMap<String, List<Double>> paretoScores) {
        var bestId = currentBestConfig.id();
        var bestScore = Double.NEGATIVE_INFINITY;
        for (var entry : paretoScores.entrySet()) {
            var score = average(entry.getValue());
            if (score > bestScore) {
                bestId = entry.getKey();
                bestScore = score;
            }
        }
        return promptConfigurations.get(bestId);
    }

    private static double average(List<Double> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static double elapsedSeconds(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000_000.0;
    }

    private static String inputs(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            return singleTurnGolden.input();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            return String.join("\n", (conversationalGolden.turns() == null ? List.<Turn>of()
                    : conversationalGolden.turns()).stream()
                    .filter(turn -> "user".equals(turn.role()))
                    .map(Turn::content)
                    .toList());
        }
        return String.valueOf(golden);
    }

    private static String expectedText(Object golden) {
        if (golden instanceof Golden singleTurnGolden) {
            return singleTurnGolden.expectedOutput();
        }
        if (golden instanceof ConversationalGolden conversationalGolden) {
            return conversationalGolden.expectedOutcome();
        }
        return null;
    }

    private record CandidateScore(PromptConfiguration configuration, double score) {
    }
}
