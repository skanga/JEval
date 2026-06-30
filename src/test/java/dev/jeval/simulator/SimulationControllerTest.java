package dev.jeval.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SimulationControllerTest {

    @Test
    void proceedAndEndMatchDeepEvalDecisionHelpers() {
        assertFalse(SimulationController.proceed().shouldEnd());
        assertEquals(null, SimulationController.proceed().reason());

        var end = SimulationController.end("done");

        assertTrue(end.shouldEnd());
        assertEquals("done", end.reason());
    }

    @Test
    void customControllerReceivesBuiltContextAndCanEndSimulation() {
        var seen = new AtomicReference<SimulationContext>();
        var controller = SimulationController.custom(context -> {
            seen.set(context);
            return SimulationController.end("limit reached");
        });
        var turns = new ArrayList<>(List.of(
                new Turn("user", "hello"),
                new Turn("assistant", "hi"),
                new Turn("user", "I need help")));
        var golden = golden(null);

        var shouldEnd = controller.run(turns, golden, 4, "thread-1", 2, 5);
        turns.add(new Turn("assistant", "mutated"));

        assertTrue(shouldEnd);
        assertEquals(3, seen.get().turns().size());
        assertEquals(golden, seen.get().golden());
        assertEquals(4, seen.get().index());
        assertEquals("thread-1", seen.get().threadId());
        assertEquals(2, seen.get().simulatedUserTurns());
        assertEquals(5, seen.get().maxUserSimulations());
        assertEquals("I need help", seen.get().lastUserTurn().content());
        assertEquals("hi", seen.get().lastAssistantTurn().content());
    }

    @Test
    void invalidControllerReturnProceedsLikeDeepEval() {
        var controller = SimulationController.custom(context -> null);

        assertFalse(controller.run(List.of(), golden(null), 0, "thread", 0, 2));
    }

    @Test
    void expectedOutcomeControllerReturnsFalseWhenOutcomeMissing() {
        var model = new ScriptedModel(List.of("{\"is_complete\":true,\"reason\":\"done\"}"));
        var controller = SimulationController.expectedOutcome(model);

        assertFalse(controller.run(List.of(), golden(null), 0, "thread", 0, 2));
        assertEquals(List.of(), model.prompts());
    }

    @Test
    void expectedOutcomeControllerUsesTemplateAndParsesCompletion() {
        var model = new ScriptedModel(List.of("{\"is_complete\":true,\"reason\":\"done\"}"));
        var controller = SimulationController.expectedOutcome(model);
        var turns = List.of(
                new Turn("user", "I reset my password."),
                new Turn("assistant", "You are all set."));

        var shouldEnd = controller.run(
                turns,
                golden("The user has successfully reset their password."),
                0,
                "thread",
                1,
                3);

        assertTrue(shouldEnd);
        assertEquals(1, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("Conversation Completion Checker"));
        assertTrue(model.prompts().getFirst().contains("The user has successfully reset their password."));
        assertTrue(model.prompts().getFirst().contains("\"content\" : \"You are all set.\""));
    }

    private static ConversationalGolden golden(String expectedOutcome) {
        return ConversationalGolden.builder("account help")
                .expectedOutcome(expectedOutcome)
                .userDescription("Pat")
                .build();
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
            return responses.get(nextResponse++);
        }

        private List<String> prompts() {
            return prompts;
        }
    }
}
