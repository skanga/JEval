package dev.jeval.optimizer.algorithms;

import java.util.Random;

public final class SIMBA {
    private final int iterations;
    private final int minibatchSize;
    private final int numCandidates;
    private final int numSamples;
    private final int minibatchFullEvalSteps;
    private final int seed;
    private final Random randomState;

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
            int minibatchFullEvalSteps, Random randomState) {
        this(iterations, minibatchSize, numCandidates, numSamples, minibatchFullEvalSteps,
                new Random().nextInt(1_000_000), randomState);
    }

    private SIMBA(int iterations, int minibatchSize, int numCandidates, int numSamples,
            int minibatchFullEvalSteps, int seed, Random randomState) {
        this.iterations = iterations;
        this.minibatchSize = minibatchSize;
        this.numCandidates = numCandidates;
        this.numSamples = numSamples;
        this.minibatchFullEvalSteps = minibatchFullEvalSteps;
        this.seed = seed;
        this.randomState = randomState == null ? new Random(seed) : randomState;
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
}
