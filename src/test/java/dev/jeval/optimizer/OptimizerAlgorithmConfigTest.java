package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.optimizer.algorithms.COPRO;
import dev.jeval.optimizer.algorithms.GEPA;
import dev.jeval.optimizer.algorithms.SIMBA;
import dev.jeval.optimizer.policies.TieBreaker;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OptimizerAlgorithmConfigTest {

    @Test
    void simbaDefaultsMatchDeepEval() {
        var algo = new SIMBA();

        assertEquals(8, algo.iterations());
        assertEquals(15, algo.minibatchSize());
        assertEquals(4, algo.numCandidates());
        assertEquals(3, algo.numSamples());
        assertEquals(4, algo.minibatchFullEvalSteps());
        assertInstanceOf(Random.class, algo.randomState());
        assertInstanceOf(Integer.class, algo.seed());
    }

    @Test
    void simbaAcceptsExplicitRandomStateAndSeed() {
        var random = new Random(42);
        var withRandom = new SIMBA(random);
        var withSeed = new SIMBA(7);

        assertSame(random, withRandom.randomState());
        assertEquals(7, withSeed.seed());
        assertInstanceOf(Random.class, withSeed.randomState());
    }

    @Test
    void simbaAllowsMinimalHyperparameters() {
        var algo = new SIMBA(1, 2, 1, 2, 1, 0);

        assertEquals(1, algo.iterations());
        assertEquals(1, algo.numCandidates());
    }

    @Test
    void coproDefaultsMatchDeepEval() {
        var algo = new COPRO();

        assertEquals(4, algo.depth());
        assertEquals(7, algo.breadth());
        assertEquals(25, algo.minibatchSize());
        assertInstanceOf(Random.class, algo.randomState());
        assertInstanceOf(Integer.class, algo.seed());
    }

    @Test
    void coproAcceptsExplicitRandomStateAndSeed() {
        var random = new Random(123);
        var withRandom = new COPRO(random);
        var withSeed = new COPRO(99);

        assertSame(random, withRandom.randomState());
        assertEquals(99, withSeed.seed());
        assertInstanceOf(Random.class, withSeed.randomState());
    }

    @Test
    void coproAllowsMinimalHyperparameters() {
        var algo = new COPRO(1, 1, 1, 0);

        assertEquals(1, algo.depth());
        assertEquals(1, algo.breadth());
        assertEquals(1, algo.minibatchSize());
    }

    @Test
    void gepaDefaultsMatchDeepEval() {
        var algo = new GEPA();

        assertEquals(5, algo.iterations());
        assertEquals(8, algo.minibatchSize());
        assertEquals(3, algo.paretoSize());
        assertEquals(3, algo.patience());
        assertEquals(TieBreaker.PREFER_CHILD, algo.tieBreaker());
        assertInstanceOf(Integer.class, algo.randomSeed());
    }

    @Test
    void gepaPreservesExplicitSeedAndTieBreaker() {
        assertEquals(123, new GEPA(123).randomSeed());
        assertEquals(0, new GEPA(0).randomSeed());
        assertEquals(TieBreaker.RANDOM, new GEPA(TieBreaker.RANDOM).tieBreaker());
    }

    @Test
    void gepaRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> new GEPA(0, 8, 3, null, 3, TieBreaker.PREFER_CHILD));
        assertThrows(IllegalArgumentException.class, () -> new GEPA(5, 0, 3, null, 3, TieBreaker.PREFER_CHILD));
        assertThrows(IllegalArgumentException.class, () -> new GEPA(5, 8, 0, null, 3, TieBreaker.PREFER_CHILD));
    }
}
