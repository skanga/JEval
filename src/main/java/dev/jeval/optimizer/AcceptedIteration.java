package dev.jeval.optimizer;

public record AcceptedIteration(String parent, String child, String module, double before, double after) {
}
