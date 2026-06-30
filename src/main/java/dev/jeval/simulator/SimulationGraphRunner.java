package dev.jeval.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import dev.jeval.metrics.MetricUtils;
import java.util.List;

public final class SimulationGraphRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SimulationNode root;

    public SimulationGraphRunner(SimulationNode root) {
        if (root == null) {
            throw new IllegalArgumentException("simulation_graph must be a SimulationNode (the root of the graph).");
        }
        this.root = root;
    }

    public GraphConversationState newConversationState() {
        return new GraphConversationState(root);
    }

    public TurnEmission run(
            GraphConversationState state,
            List<Turn> turns,
            ConversationalGolden golden,
            String threadId,
            String language) {
        var node = state.current();
        var visits = state.visits(node);
        if (node.maxVisits() != null && visits >= node.maxVisits()) {
            return new TurnEmission(null, true);
        }
        var turn = node.emit(new SimulationContext(
                turns,
                golden,
                0,
                threadId,
                visits,
                node.maxVisits() == null ? Integer.MAX_VALUE : node.maxVisits(),
                lastTurn(turns, "user"),
                lastTurn(turns, "assistant")));
        state.incrementVisits(node);
        return new TurnEmission(turn, node.terminal());
    }

    public void advance(EvaluationModel model, GraphConversationState state, String assistantReply) {
        var node = state.current();
        if (node.edges().isEmpty()) {
            return;
        }
        var choices = node.edges().stream().map(SimulationNode.Edge::when).toList();
        var prompt = SimulationGraphTemplate.classifyEdge(assistantReply, choices);
        var choice = parseChoice(model.generate(prompt));
        var next = resolveChoice(node, choice);
        if (next != null) {
            state.current(next);
        }
    }

    private static SimulationNode resolveChoice(SimulationNode node, EdgeChoice choice) {
        if (choice.index() == null || choice.index() < 1 || choice.index() > node.edges().size()) {
            return null;
        }
        return node.edges().get(choice.index() - 1).child();
    }

    private static EdgeChoice parseChoice(String json) {
        try {
            return JSON.treeToValue(MetricUtils.trimAndLoadJson(json), EdgeChoice.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse simulation graph edge choice JSON", error);
        }
    }

    private static Turn lastTurn(List<Turn> turns, String role) {
        if (turns == null) {
            return null;
        }
        for (var index = turns.size() - 1; index >= 0; index--) {
            var turn = turns.get(index);
            if (role.equals(turn.role())) {
                return turn;
            }
        }
        return null;
    }
}
