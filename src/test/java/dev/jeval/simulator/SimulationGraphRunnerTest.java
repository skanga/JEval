package dev.jeval.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationGraphRunnerTest {

    @Test
    void rejectsNonRootNodesLikeDeepEval() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationGraphRunner(null));
    }

    @Test
    void runEmitsUserTurnAndTracksVisitsPerConversationState() {
        var root = SimulationNode.ofText(context -> "first user", false, 1, "root");
        var runner = new SimulationGraphRunner(root);
        var state = runner.newConversationState();

        var first = runner.run(state, List.of(), golden(), "thread-1", "English");
        var second = runner.run(state, List.of(), golden(), "thread-1", "English");
        var fresh = runner.run(runner.newConversationState(), List.of(), golden(), "thread-2", "English");

        assertEquals(new Turn("user", "first user"), first.turn());
        assertFalse(first.end());
        assertNull(second.turn());
        assertTrue(second.end());
        assertEquals(new Turn("user", "first user"), fresh.turn());
    }

    @Test
    void terminalNodeEmitsOneFinalTurnThenEnds() {
        var root = SimulationNode.ofText(context -> "last user", true, null, "terminal");
        var runner = new SimulationGraphRunner(root);

        var emission = runner.run(runner.newConversationState(), List.of(), golden(), "thread-1", "English");

        assertEquals(new Turn("user", "last user"), emission.turn());
        assertTrue(emission.end());
    }

    @Test
    void graphActionReceivesOwningSimulatorLikeDeepEval() {
        var root = SimulationNode.ofText(context -> {
            assertTrue(context.simulator() instanceof ConversationSimulator);
            return "simulator=" + context.simulator().language();
        }, true, null, "simulator-aware");
        var model = new ScriptedModel(List.of());
        var simulator = new ConversationSimulator(
                context -> new Turn("assistant", "reply"),
                model,
                "Spanish",
                root,
                SimulationController.custom(context -> SimulationController.proceed()));

        var testCase = simulator.simulate(golden(), 1);

        assertEquals("simulator=Spanish", testCase.turns().getFirst().content());
    }

    @Test
    void advanceRoutesToSelectedEdgeAndStaysOnNoneOrOutOfRangeLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                """
                Routing result:
                ```json
                {"index":2,"reason":"assistant denied it",}
                ```
                """,
                "{\"index\":null,\"reason\":\"none match\"}",
                "{\"index\":9,\"reason\":\"bad route\"}"));
        var root = SimulationNode.ofText(context -> "start", false, null, "root");
        var approved = SimulationNode.ofText(context -> "approved", false, null, "approved");
        var denied = SimulationNode.ofText(context -> "denied", false, null, "denied");
        root.addNode(approved, "assistant approved the refund");
        root.addNode(denied, "assistant denied the refund");
        var runner = new SimulationGraphRunner(root);
        var selectedState = runner.newConversationState();
        var noneState = runner.newConversationState();
        var outOfRangeState = runner.newConversationState();

        runner.advance(model, selectedState, "No refund is available.");
        var routed = runner.run(selectedState, List.of(), golden(), "thread-1", "English");
        runner.advance(model, noneState, "This does not match.");
        var afterNone = runner.run(noneState, List.of(), golden(), "thread-1", "English");
        runner.advance(model, outOfRangeState, "Invalid index.");
        var afterOutOfRange = runner.run(outOfRangeState, List.of(), golden(), "thread-1", "English");

        assertEquals("denied", routed.turn().content());
        assertEquals("start", afterNone.turn().content());
        assertEquals("start", afterOutOfRange.turn().content());
        assertEquals(3, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("  1) assistant approved the refund"));
        assertTrue(model.prompts().getFirst().contains("  2) assistant denied the refund"));
    }

    @Test
    void advanceDoesNotCallModelWhenNodeHasNoEdges() {
        var model = new ScriptedModel(List.of());
        var root = SimulationNode.ofText(context -> "start", false, null, "root");
        var runner = new SimulationGraphRunner(root);

        runner.advance(model, runner.newConversationState(), "anything");

        assertEquals(List.of(), model.prompts());
    }

    private static ConversationalGolden golden() {
        return ConversationalGolden.builder("refund scenario").build();
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();
        private int nextResponse;

        private ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            if (nextResponse >= responses.size()) {
                throw new IllegalArgumentException("No response");
            }
            return responses.get(nextResponse++);
        }

        private List<String> prompts() {
            return prompts;
        }
    }
}
