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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

public final class COPRO implements PromptOptimizationAlgorithm {
    private final int depth;
    private final int breadth;
    private final int minibatchSize;
    private final int seed;
    private final Random randomState;
    private final Function<String, String> proposeCallback;

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

    public COPRO(int depth, int breadth, int minibatchSize, int randomState,
            Function<String, String> proposeCallback) {
        this(depth, breadth, minibatchSize, randomState, new Random(randomState), proposeCallback);
    }

    public COPRO(int depth, int breadth, int minibatchSize, Random randomState) {
        this(depth, breadth, minibatchSize, new Random().nextInt(1_000_000), randomState);
    }

    private COPRO(int depth, int breadth, int minibatchSize, int seed, Random randomState) {
        this(depth, breadth, minibatchSize, seed, randomState, null);
    }

    private COPRO(int depth, int breadth, int minibatchSize, int seed, Random randomState,
            Function<String, String> proposeCallback) {
        this.depth = depth;
        this.breadth = breadth;
        this.minibatchSize = minibatchSize;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
        this.proposeCallback = proposeCallback;
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
        var bestConfig = rootConfig;
        var bestScore = average(scores);
        var acceptedIterations = new ArrayList<AcceptedIteration>();

        if (proposeCallback != null) {
            var proposer = new COPROProposer(proposeCallback);
            var candidates = new ArrayList<Prompt>();
            candidates.add(prompt);
            candidates.addAll(proposer.proposeBootstrap(prompt, breadth));
            var history = new ArrayList<CandidateHistory>();
            var globalBestSeen = false;
            bestScore = Double.NEGATIVE_INFINITY;

            for (var d = 0; d < depth; d++) {
                var minibatch = sampleMinibatch(goldens);
                var results = evaluateCandidates(candidates, minibatch, scorer, promptConfigurations);
                if (results.isEmpty()) {
                    break;
                }
                results.sort(Comparator.comparingDouble(CandidateResult::score).reversed());
                var bestBatch = results.getFirst();

                for (var result : results.subList(0, Math.min(breadth, results.size()))) {
                    history.add(new CandidateHistory(result.prompt(), result.score(), result.feedback()));
                }
                history.sort(Comparator.comparingDouble(CandidateHistory::score).reversed());
                if (history.size() > breadth) {
                    history = new ArrayList<>(history.subList(0, breadth));
                }

                var fullScores = scorer.scorePareto(bestBatch.configuration(), goldens);
                var fullScore = average(fullScores);
                paretoScores.put(bestBatch.configuration().id(), fullScores);
                parents.putIfAbsent(bestBatch.configuration().id(), null);
                if (fullScore > bestScore) {
                    if (globalBestSeen) {
                        acceptedIterations.add(new AcceptedIteration(
                                bestConfig.id(),
                                bestBatch.configuration().id(),
                                OptimizerScorer.DEFAULT_MODULE_ID,
                                bestScore,
                                fullScore));
                        parents.put(bestBatch.configuration().id(), bestConfig.id());
                    }
                    bestConfig = bestBatch.configuration();
                    bestScore = fullScore;
                    globalBestSeen = true;
                }

                if (d < depth - 1) {
                    candidates = new ArrayList<>(proposer.proposeFromHistory(
                            bestBatch.prompt(),
                            historyText(history),
                            breadth));
                    if (candidates.isEmpty()) {
                        candidates.add(bestBatch.prompt());
                    }
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

    private List<?> sampleMinibatch(List<?> goldens) {
        if (goldens.size() <= minibatchSize) {
            return goldens;
        }
        var copy = new ArrayList<>(goldens);
        Collections.shuffle(copy, randomState);
        return List.copyOf(copy.subList(0, minibatchSize));
    }

    private static List<CandidateResult> evaluateCandidates(
            List<Prompt> candidates,
            List<?> minibatch,
            OptimizerScorer scorer,
            LinkedHashMap<String, PromptConfiguration> promptConfigurations) {
        var results = new ArrayList<CandidateResult>();
        for (var candidate : candidates) {
            var candidatePrompts = new LinkedHashMap<String, Prompt>();
            candidatePrompts.put(OptimizerScorer.DEFAULT_MODULE_ID, candidate);
            var candidateConfig = PromptConfiguration.create(candidatePrompts);
            promptConfigurations.put(candidateConfig.id(), candidateConfig);
            var score = scorer.scoreMinibatch(candidateConfig, minibatch);
            var diagnosis = scorer.getMinibatchFeedback(candidateConfig, OptimizerScorer.DEFAULT_MODULE_ID, minibatch);
            var feedback = diagnosis.failures().isBlank() ? "All metrics passed perfectly." : diagnosis.failures();
            results.add(new CandidateResult(candidate, candidateConfig, score, feedback));
        }
        return results;
    }

    private static String historyText(List<CandidateHistory> history) {
        var rows = new ArrayList<String>();
        for (var i = 0; i < history.size(); i++) {
            var item = history.get(i);
            rows.add("Attempt " + (i + 1) + "\n"
                    + "Prompt:\n" + OptimizerUtils.parsePrompt(item.prompt()) + "\n"
                    + "Score: " + item.score() + "\n"
                    + "Evaluation Feedback:\n" + item.feedback());
        }
        return String.join("\n---\n", rows);
    }

    private static double average(List<Double> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private record CandidateResult(
            Prompt prompt,
            PromptConfiguration configuration,
            double score,
            String feedback) {
    }

    private record CandidateHistory(Prompt prompt, double score, String feedback) {
    }
}
