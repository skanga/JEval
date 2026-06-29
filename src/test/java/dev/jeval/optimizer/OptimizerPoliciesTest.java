package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.optimizer.policies.OptimizerPolicies;
import dev.jeval.optimizer.policies.TieBreaker;
import java.util.LinkedHashMap;
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
}
