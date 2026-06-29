package dev.jeval.optimizer.algorithms;

import dev.jeval.optimizer.policies.TieBreaker;
import java.util.Random;

public final class GEPA {
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
}
