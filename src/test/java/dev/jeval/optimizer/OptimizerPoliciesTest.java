package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.optimizer.policies.OptimizerPolicies;
import dev.jeval.optimizer.policies.TieBreaker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizerPoliciesTest {

    @Test
    void pickBestWithTiesChoosesSingleCandidate() {
        var parents = new LinkedHashMap<String, String>();
        parents.put("p1", null);

        var result = OptimizerPolicies.pickBestWithTies(
                new LinkedHashMap<>(Map.of("p1", 0.42)),
                parents,
                new Random(123));

        assertEquals("p1", result.chosenId());
        assertEquals(Set.of("p1"), Set.copyOf(result.tiedIds()));
        assertEquals(0.42, result.maxScore());
    }

    @Test
    void pickBestWithTiesRejectsEmptyTotals() {
        assertThrows(IllegalArgumentException.class,
                () -> OptimizerPolicies.pickBestWithTies(Map.of(), Map.of(), new Random(123)));
    }

    @Test
    void pickBestWithTiesRejectsNonFiniteScores() {
        var parents = new LinkedHashMap<String, String>();
        parents.put("p1", null);

        assertThrows(IllegalArgumentException.class,
                () -> OptimizerPolicies.pickBestWithTies(Map.of("p1", Double.NaN), parents, new Random(123)));
        assertThrows(IllegalArgumentException.class,
                () -> new OptimizerPolicies.TieBreakResult("p1", List.of("p1"), Double.POSITIVE_INFINITY));
    }

    @Test
    void pickBestWithTiesPrefersChildWhenTied() {
        var totals = new LinkedHashMap<String, Double>();
        totals.put("root", 0.8);
        totals.put("child", 0.8);
        var parents = new LinkedHashMap<String, String>();
        parents.put("root", null);
        parents.put("child", "root");

        var result = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(123), 1e-9, TieBreaker.PREFER_CHILD);

        assertEquals(Set.of("root", "child"), Set.copyOf(result.tiedIds()));
        assertEquals("child", result.chosenId());
        assertEquals(0.8, result.maxScore());
    }

    @Test
    void pickBestWithTiesPrefersRootWhenTied() {
        var totals = new LinkedHashMap<String, Double>();
        totals.put("root", 0.8);
        totals.put("child", 0.8);
        var parents = new LinkedHashMap<String, String>();
        parents.put("root", null);
        parents.put("child", "root");

        var result = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(123), 1e-9, TieBreaker.PREFER_ROOT);

        assertEquals(Set.of("root", "child"), Set.copyOf(result.tiedIds()));
        assertEquals("root", result.chosenId());
        assertEquals(0.8, result.maxScore());
    }

    @Test
    void randomTiePolicyIsDeterministicWithSeed() {
        var totals = new LinkedHashMap<String, Double>();
        totals.put("a", 1.0);
        totals.put("b", 1.0);
        totals.put("c", 1.0);
        var parents = new LinkedHashMap<String, String>();
        parents.put("a", null);
        parents.put("b", null);
        parents.put("c", null);

        var first = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(7), 1e-9, TieBreaker.RANDOM);
        var second = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(7), 1e-9, TieBreaker.RANDOM);

        assertEquals(Set.of("a", "b", "c"), Set.copyOf(first.tiedIds()));
        assertEquals(first.chosenId(), second.chosenId());
        assertEquals(1.0, first.maxScore());
        assertEquals(first.maxScore(), second.maxScore());
    }

    @Test
    void pickBestWithTiesRespectsTieTolerance() {
        var totals = new LinkedHashMap<String, Double>();
        totals.put("a", 1.0);
        totals.put("b", 1.005);
        var parents = new LinkedHashMap<String, String>();
        parents.put("a", null);
        parents.put("b", null);

        var strict = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(123), 1e-4, TieBreaker.PREFER_ROOT);
        var loose = OptimizerPolicies.pickBestWithTies(
                totals, parents, new Random(123), 0.01, TieBreaker.PREFER_ROOT);

        assertEquals("b", strict.chosenId());
        assertEquals(Set.of("b"), Set.copyOf(strict.tiedIds()));
        assertEquals(Set.of("a", "b"), Set.copyOf(loose.tiedIds()));
        assertEquals("a", loose.chosenId());
    }

    @Test
    void dominatedRequiresOtherToBeAtLeastAsGoodAndBetterByDelta() {
        assertTrue(OptimizerPolicies.isDominated(List.of(0.7, 0.5), List.of(0.8, 0.5), 0.01));
        assertFalse(OptimizerPolicies.isDominated(List.of(0.7, 0.5), List.of(0.705, 0.5), 0.01));
        assertFalse(OptimizerPolicies.isDominated(List.of(0.7, 0.5), List.of(0.8, 0.4), 0.01));
    }

    @Test
    void paretoFrontierReturnsNonDominatedCandidatesInInputOrder() {
        var scores = new LinkedHashMap<String, List<Double>>();
        scores.put("a", List.of(0.8, 0.8));
        scores.put("b", List.of(0.9, 0.7));
        scores.put("c", List.of(0.7, 0.7));

        assertEquals(List.of("a", "b"), OptimizerPolicies.paretoFrontier(List.of("a", "b", "c"), scores));
    }

    @Test
    void frequencyWeightsCountsPerInstanceWinnersOnGlobalFrontier() {
        var scores = new LinkedHashMap<String, List<Double>>();
        scores.put("root", List.of(0.9, 0.4, 0.9));
        scores.put("child", List.of(0.8, 0.9, 0.9));
        scores.put("weak", List.of(0.1, 0.1, 0.1));

        var weights = OptimizerPolicies.frequencyWeights(scores);

        assertEquals(2, weights.get("root"));
        assertEquals(2, weights.get("child"));
        assertFalse(weights.containsKey("weak"));
    }

    @Test
    void sampleByFrequencyRejectsEmptyAndUsesUniformFallbackForZeroWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> OptimizerPolicies.sampleByFrequency(Map.of(), new Random(1)));

        var weights = new LinkedHashMap<String, Integer>();
        weights.put("a", 0);
        weights.put("b", 0);

        assertEquals(
                OptimizerPolicies.sampleByFrequency(weights, new Random(3)),
                OptimizerPolicies.sampleByFrequency(weights, new Random(3)));
    }

    @Test
    void selectPromptConfigurationParetoSamplesFromFrequencyWeights() {
        var scores = new LinkedHashMap<String, List<Double>>();
        scores.put("root", List.of(0.9, 0.4));
        scores.put("child", List.of(0.8, 0.9));
        scores.put("weak", List.of(0.1, 0.1));

        var selected = OptimizerPolicies.selectPromptConfigurationPareto(scores, new Random(7));

        assertTrue(Set.of("root", "child").contains(selected));
    }
}
