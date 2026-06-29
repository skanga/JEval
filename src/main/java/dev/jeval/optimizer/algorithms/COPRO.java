package dev.jeval.optimizer.algorithms;

import java.util.Random;

public final class COPRO {
    private final int depth;
    private final int breadth;
    private final int minibatchSize;
    private final int seed;
    private final Random randomState;

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

    public COPRO(int depth, int breadth, int minibatchSize, Random randomState) {
        this(depth, breadth, minibatchSize, new Random().nextInt(1_000_000), randomState);
    }

    private COPRO(int depth, int breadth, int minibatchSize, int seed, Random randomState) {
        this.depth = depth;
        this.breadth = breadth;
        this.minibatchSize = minibatchSize;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
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
}
