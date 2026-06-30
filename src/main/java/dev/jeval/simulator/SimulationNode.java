package dev.jeval.simulator;

import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;

public final class SimulationNode {
    private final TurnAction action;
    private final boolean terminal;
    private final Integer maxVisits;
    private final String name;
    private final List<Edge> edges = new ArrayList<>();

    private SimulationNode(TurnAction action, boolean terminal, Integer maxVisits, String name) {
        if (action == null) {
            throw new IllegalArgumentException("`action` must be a callable returning str or Turn.");
        }
        if (maxVisits != null && maxVisits <= 0) {
            throw new IllegalArgumentException("`max_visits` must be a positive integer.");
        }
        this.action = action;
        this.terminal = terminal;
        this.maxVisits = maxVisits;
        this.name = name == null || name.isBlank() ? "simulation_node" : name;
    }

    public static SimulationNode ofTurn(TurnAction action) {
        return ofTurn(action, false, null, null);
    }

    public static SimulationNode ofTurn(TurnAction action, boolean terminal, Integer maxVisits, String name) {
        return new SimulationNode(action, terminal, maxVisits, name);
    }

    public static SimulationNode ofText(TextAction action) {
        return ofText(action, false, null, null);
    }

    public static SimulationNode ofText(TextAction action, boolean terminal, Integer maxVisits, String name) {
        if (action == null) {
            throw new IllegalArgumentException("`action` must be a callable returning str or Turn.");
        }
        return new SimulationNode(context -> new Turn("user", action.apply(context)), terminal, maxVisits, name);
    }

    public SimulationNode addNode(SimulationNode child, String when) {
        if (child == null) {
            throw new IllegalArgumentException("`child` must be a SimulationNode instance.");
        }
        if (when == null || when.isBlank()) {
            throw new IllegalArgumentException("`when=` must be a non-empty string description.");
        }
        edges.add(new Edge(child, when));
        return child;
    }

    public Turn emit(SimulationContext context) {
        var turn = action.apply(context);
        if (turn == null || !"user".equals(turn.role())) {
            throw new IllegalArgumentException("`action` must return a string or Turn with role='user'.");
        }
        return turn;
    }

    public boolean terminal() {
        return terminal;
    }

    public Integer maxVisits() {
        return maxVisits;
    }

    public String name() {
        return name;
    }

    public List<Edge> edges() {
        return List.copyOf(edges);
    }

    @Override
    public String toString() {
        return "SimulationNode(name='" + name.replace("\\", "\\\\").replace("'", "\\'") + "', terminal="
                + (terminal ? "True" : "False") + ", max_visits="
                + (maxVisits == null ? "None" : maxVisits) + ", edges=" + edges.size() + ")";
    }

    @FunctionalInterface
    public interface TurnAction {
        Turn apply(SimulationContext context);
    }

    @FunctionalInterface
    public interface TextAction {
        String apply(SimulationContext context);
    }

    public record Edge(SimulationNode child, String when) {
    }
}
