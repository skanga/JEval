package dev.jeval.optimizer;

public record AcceptedIteration(String parent, String child, String module, double before, double after) {
    public AcceptedIteration {
        if (!Double.isFinite(before)) {
            throw new IllegalArgumentException("AcceptedIteration before score must be finite");
        }
        if (!Double.isFinite(after)) {
            throw new IllegalArgumentException("AcceptedIteration after score must be finite");
        }
    }
}
