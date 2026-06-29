package dev.jeval.optimizer.algorithms;

import dev.jeval.DeepEvalException;
import dev.jeval.optimizer.AcceptedIteration;
import dev.jeval.optimizer.OptimizationReport;
import dev.jeval.optimizer.OptimizationResult;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.optimizer.OptimizerUtils;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.PromptOptimizationAlgorithm;
import dev.jeval.optimizer.ScorerDiagnosisResult;
import dev.jeval.optimizer.policies.OptimizerPolicies;
import dev.jeval.optimizer.policies.TieBreaker;
import dev.jeval.optimizer.rewriter.Rewriter;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptMessage;
import dev.jeval.prompt.PromptType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;

public final class GEPA implements PromptOptimizationAlgorithm {
    private static final double MIN_DELTA = 0.0;

    private final int iterations;
    private final int minibatchSize;
    private final int paretoSize;
    private final int randomSeed;
    private final int patience;
    private final TieBreaker tieBreaker;
    private final Random randomState;
    private final Function<String, String> rewriteCallback;

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
        this(iterations, minibatchSize, paretoSize, randomSeed, patience, tieBreaker, null);
    }

    public GEPA(int iterations, int minibatchSize, int paretoSize, Integer randomSeed, int patience,
            TieBreaker tieBreaker, Function<String, String> rewriteCallback) {
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
        this.rewriteCallback = rewriteCallback;
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

    <T> List<T> drawMinibatch(List<T> goldens) {
        var size = Math.min(minibatchSize, goldens.size());
        var sampled = new ArrayList<T>(size);
        for (var i = 0; i < size; i++) {
            sampled.add(goldens.get(randomState.nextInt(goldens.size())));
        }
        return List.copyOf(sampled);
    }

    boolean isEquivalentPrompt(Prompt original, Prompt rewritten) {
        if (original.type() != rewritten.type()) {
            return false;
        }
        if (original.type() == PromptType.TEXT) {
            return normalized(original.textTemplate()).equals(normalized(rewritten.textTemplate()));
        }
        if (original.type() == PromptType.LIST) {
            return equivalentMessages(original.messagesTemplate(), rewritten.messagesTemplate());
        }
        return false;
    }

    PromptConfiguration childConfiguration(PromptConfiguration parent, String moduleId, Prompt rewrittenPrompt) {
        var prompts = new LinkedHashMap<>(parent.prompts());
        prompts.put(moduleId, rewrittenPrompt);
        return PromptConfiguration.create(prompts, parent.id());
    }

    Prompt generateChildPrompt(
            PromptConfiguration parent,
            String moduleId,
            ScorerDiagnosisResult feedbackDiagnosis) {
        if (rewriteCallback == null) {
            return null;
        }
        var original = parent.prompts().get(moduleId);
        if (original == null) {
            return null;
        }
        var rewritten = new Rewriter(rewriteCallback).rewrite(original, feedbackDiagnosis);
        return isEquivalentPrompt(original, rewritten) ? null : rewritten;
    }

    boolean acceptChild(
            PromptConfiguration parent,
            PromptConfiguration child,
            String moduleId,
            List<Double> parentScores,
            List<Double> childScores,
            Map<String, List<Double>> paretoScores,
            Map<String, String> parents,
            Map<String, PromptConfiguration> promptConfigurations,
            List<AcceptedIteration> acceptedIterations) {
        var before = average(parentScores);
        var after = average(childScores);
        if (!shouldAcceptChild(childScores, parentScores, paretoScores)) {
            return false;
        }
        paretoScores.put(child.id(), List.copyOf(childScores));
        parents.put(child.id(), parent.id());
        promptConfigurations.put(child.id(), child);
        acceptedIterations.add(new AcceptedIteration(parent.id(), child.id(), moduleId, before, after));
        paretoScores.entrySet().removeIf(entry -> !entry.getKey().equals(child.id())
                && OptimizerPolicies.isDominated(entry.getValue(), childScores, MIN_DELTA));
        return true;
    }

    PromptConfiguration bestByAggregate(
            Map<String, List<Double>> paretoScores,
            Map<String, String> parents,
            Map<String, PromptConfiguration> promptConfigurations) {
        var totals = new LinkedHashMap<String, Double>();
        for (var entry : paretoScores.entrySet()) {
            totals.put(entry.getKey(), average(entry.getValue()));
        }
        var chosen = OptimizerPolicies.pickBestWithTies(
                totals,
                parents,
                randomState,
                1e-9,
                tieBreaker);
        return promptConfigurations.get(chosen.chosenId());
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
        var acceptedIterations = new ArrayList<AcceptedIteration>();

        for (var i = 0; i < iterations && !split.feedback().isEmpty(); i++) {
            var parent = bestByAggregate(paretoScores, parents, promptConfigurations);
            var minibatch = drawMinibatch(split.feedback());
            var feedback = scorer.getMinibatchFeedback(parent, OptimizerScorer.DEFAULT_MODULE_ID, minibatch);
            var childPrompt = generateChildPrompt(parent, OptimizerScorer.DEFAULT_MODULE_ID, feedback);
            if (childPrompt == null) {
                continue;
            }
            var parentMinibatchScore = scorer.scoreMinibatch(parent, minibatch);
            var child = childConfiguration(parent, OptimizerScorer.DEFAULT_MODULE_ID, childPrompt);
            var childMinibatchScore = scorer.scoreMinibatch(child, minibatch);
            if (childMinibatchScore <= parentMinibatchScore) {
                continue;
            }
            acceptChild(
                    parent,
                    child,
                    OptimizerScorer.DEFAULT_MODULE_ID,
                    paretoScores.get(parent.id()),
                    scorer.scorePareto(child, paretoGoldens),
                    paretoScores,
                    parents,
                    promptConfigurations,
                    acceptedIterations);
        }

        var best = bestByAggregate(paretoScores, parents, promptConfigurations);

        var report = new OptimizationReport(
                optimizationId,
                best.id(),
                acceptedIterations,
                paretoScores,
                parents,
                OptimizerUtils.buildPromptConfigSnapshots(promptConfigurations));
        return new OptimizationResult(best.prompts().get(OptimizerScorer.DEFAULT_MODULE_ID), report);
    }

    private static boolean equivalentMessages(List<PromptMessage> original, List<PromptMessage> rewritten) {
        if (original.size() != rewritten.size()) {
            return false;
        }
        for (var i = 0; i < original.size(); i++) {
            if (!original.get(i).role().equals(rewritten.get(i).role())) {
                return false;
            }
            if (!normalized(original.get(i).content()).equals(normalized(rewritten.get(i).content()))) {
                return false;
            }
        }
        return true;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean shouldAcceptChild(
            List<Double> childScores,
            List<Double> parentScores,
            Map<String, List<Double>> paretoScores) {
        if (OptimizerPolicies.isDominated(childScores, parentScores, MIN_DELTA)) {
            return false;
        }
        for (var existingScores : paretoScores.values()) {
            if (OptimizerPolicies.isDominated(childScores, existingScores, MIN_DELTA)) {
                return false;
            }
        }
        return true;
    }

    private static double average(List<Double> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        return scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
