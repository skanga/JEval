package dev.jeval.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalGolden;
import dev.jeval.Turn;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatorTypesTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesSimulatorSchemasWithDeepEvalAliases() throws Exception {
        var completion = JSON.readValue(
                "{\"is_complete\":true,\"reason\":\"goal satisfied\"}", ConversationCompletion.class);
        var simulatedInput = JSON.readValue(
                "{\"simulated_input\":\"I need a refund\"}", SimulatedInput.class);
        var edgeChoice = JSON.readValue(
                "{\"index\":2,\"reason\":\"assistant offered refund help\"}", EdgeChoice.class);
        var decision = JSON.readValue(
                "{\"should_end\":false,\"reason\":\"continue\"}", Decision.class);

        assertTrue(completion.isComplete());
        assertEquals("goal satisfied", completion.reason());
        assertEquals("I need a refund", simulatedInput.simulatedInput());
        assertEquals(2, edgeChoice.index());
        assertEquals("assistant offered refund help", edgeChoice.reason());
        assertFalse(decision.shouldEnd());
        assertEquals("continue", decision.reason());
    }

    @Test
    void simulatorContextCopiesTurnsAndStoresConversationState() {
        var golden = ConversationalGolden.builder("refund flow")
                .turns(List.of(new Turn("user", "hello")))
                .build();
        var turns = new java.util.ArrayList<>(List.of(new Turn("user", "hello")));

        var context = new SimulationContext(
                turns,
                golden,
                3,
                "thread-1",
                2,
                5,
                new Turn("user", "I need help"),
                new Turn("assistant", "How can I help?"),
                "Spanish");

        turns.add(new Turn("user", "mutated"));

        assertEquals(1, context.turns().size());
        assertEquals(golden, context.golden());
        assertEquals(3, context.index());
        assertEquals("thread-1", context.threadId());
        assertEquals(2, context.simulatedUserTurns());
        assertEquals(5, context.maxUserSimulations());
        assertEquals("I need help", context.lastUserTurn().content());
        assertEquals("How can I help?", context.lastAssistantTurn().content());
        assertEquals("Spanish", context.language());
    }

    @Test
    void simulationNodeValidatesActionMaxVisitsAndEdgesLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> SimulationNode.ofTurn(null));
        assertThrows(IllegalArgumentException.class, () -> SimulationNode.ofText(context -> "hello", false, 0, null));

        var root = SimulationNode.ofText(context -> "I need a refund", false, 2, "ask_refund");
        var child = SimulationNode.ofText(context -> "Thanks", true, null, "thanks");

        assertThrows(IllegalArgumentException.class, () -> root.addNode(null, "anything"));
        assertThrows(IllegalArgumentException.class, () -> root.addNode(child, " "));
        assertEquals(child, root.addNode(child, "assistant offers refund"));

        assertEquals(1, root.edges().size());
        assertEquals(child, root.edges().getFirst().child());
        assertEquals("assistant offers refund", root.edges().getFirst().when());
        assertEquals(
                "SimulationNode(name='ask_refund', terminal=False, max_visits=2, edges=1)",
                root.toString());
    }

    @Test
    void simulationNodeWrapsStringActionsAndRejectsNonUserTurns() {
        var node = SimulationNode.ofText(context -> "I need a refund");

        var turn = node.emit(null);

        assertEquals(new Turn("user", "I need a refund"), turn);
        var assistantNode = SimulationNode.ofTurn(context -> new Turn("assistant", "hello"));
        assertThrows(IllegalArgumentException.class, () -> assistantNode.emit(null));
    }
}
