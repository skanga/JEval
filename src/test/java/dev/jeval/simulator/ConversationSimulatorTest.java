package dev.jeval.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalGolden;
import dev.jeval.EvaluationModel;
import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ConversationSimulatorTest {

    @Test
    void defaultsLanguageToEnglishLikeDeepEval() {
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), new ScriptedModel(List.of()));

        assertEquals("English", simulator.language());
    }

    @Test
    void generateFirstUserInputUsesSimulationTemplateAndParsesSimulatedInput() {
        var model = new ScriptedModel(List.of("{\"simulated_input\":\"Hola, necesito ayuda.\"}"));
        var golden = ConversationalGolden.builder("A user needs billing help.")
                .userDescription("Alicia")
                .build();
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), model, "Spanish");

        var input = simulator.generateFirstUserInput(golden);

        assertEquals("Hola, necesito ayuda.", input);
        assertEquals(1, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("start a conversation in Spanish"));
        assertTrue(model.prompts().getFirst().contains("Alicia"));
        assertTrue(model.prompts().getFirst().contains("A user needs billing help."));
    }

    @Test
    void generateNextUserInputUsesPreviousConversationLikeDeepEval() {
        var model = new ScriptedModel(List.of("{\"simulated_input\":\"Can you check Tuesday?\"}"));
        var golden = ConversationalGolden.builder("A user is rescheduling an appointment.")
                .userDescription("Nora")
                .build();
        var turns = List.of(
                new Turn("user", "I need to move my appointment."),
                new Turn("assistant", "What day works?"));
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), model);

        var input = simulator.generateNextUserInput(golden, turns);

        assertEquals("Can you check Tuesday?", input);
        assertTrue(model.prompts().getFirst().contains("generate the next user input in English"));
        assertTrue(model.prompts().getFirst().contains("\"content\" : \"What day works?\""));
    }

    @Test
    void invalidSimulatorJsonReportsParseFailure() {
        var model = new ScriptedModel(List.of("{\"wrong\":\"shape\"}"));
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), model);

        assertThrows(IllegalArgumentException.class,
                () -> simulator.generateFirstUserInput(ConversationalGolden.builder("scenario").build()));
    }

    @Test
    void generateTurnFromCallbackPassesInputTurnsAndThreadId() {
        var seen = new AtomicReference<ConversationSimulator.CallbackContext>();
        var simulator = new ConversationSimulator(context -> {
            seen.set(context);
            return new Turn("assistant", "assistant reply to " + context.input());
        }, new ScriptedModel(List.of()));
        var turns = new ArrayList<>(List.of(new Turn("user", "hello")));

        var turn = simulator.generateTurnFromCallback("hello", turns, "thread-1");
        turns.add(new Turn("assistant", "mutated"));

        assertEquals(new Turn("assistant", "assistant reply to hello"), turn);
        assertEquals("hello", seen.get().input());
        assertEquals("thread-1", seen.get().threadId());
        assertEquals(1, seen.get().turns().size());
    }

    @Test
    void simulateGeneratesConversationUntilMaxUserSimulations() {
        var model = new ScriptedModel(List.of(
                "{\"simulated_input\":\"Hi, I need billing help.\"}",
                "{\"simulated_input\":\"Can you clarify the charge?\"}"));
        var seenInputs = new ArrayList<String>();
        var seenTurnCounts = new ArrayList<Integer>();
        var simulator = new ConversationSimulator(context -> {
            seenInputs.add(context.input());
            seenTurnCounts.add(context.turns().size());
            return new Turn("assistant", "assistant reply to " + context.input());
        }, model);
        var golden = ConversationalGolden.builder("A user needs billing help.")
                .userDescription("Maya")
                .expectedOutcome(null)
                .context(List.of("Billing policy context"))
                .additionalMetadata(Map.of("source", "seed"))
                .comments("generated from simulator")
                .name("billing flow")
                .build();

        var testCases = simulator.simulate(List.of(golden), 2);

        assertEquals(1, testCases.size());
        var testCase = testCases.getFirst();
        assertEquals("A user needs billing help.", testCase.scenario());
        assertEquals("Maya", testCase.userDescription());
        assertEquals(List.of("Billing policy context"), testCase.context());
        assertEquals("generated from simulator", testCase.comments());
        assertEquals("billing flow", testCase.name());
        assertEquals("seed", testCase.metadata().get("source"));
        assertEquals("Maya", testCase.metadata().get("User Description"));
        assertEquals(List.of(
                new Turn("user", "Hi, I need billing help."),
                new Turn("assistant", "assistant reply to Hi, I need billing help."),
                new Turn("user", "Can you clarify the charge?"),
                new Turn("assistant", "assistant reply to Can you clarify the charge?")),
                testCase.turns());
        assertEquals(List.of("Hi, I need billing help.", "Can you clarify the charge?"), seenInputs);
        assertEquals(List.of(1, 3), seenTurnCounts);
        assertEquals(2, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("start a conversation in English"));
        assertTrue(model.prompts().get(1).contains("generate the next user input in English"));
    }

    @Test
    void simulateRejectsNonPositiveMaxUserSimulations() {
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), new ScriptedModel(List.of()));
        var golden = ConversationalGolden.builder("scenario").build();

        assertThrows(IllegalArgumentException.class, () -> simulator.simulate(List.of(golden), 0));
    }

    @Test
    void simulateStopsWhenExpectedOutcomeAlreadyComplete() {
        var model = new ScriptedModel(List.of("{\"is_complete\":true,\"reason\":\"done\"}"));
        var callbacks = new AtomicInteger();
        var simulator = new ConversationSimulator(context -> {
            callbacks.incrementAndGet();
            return new Turn("assistant", "unexpected");
        }, model);
        var initialTurns = List.of(
                new Turn("user", "I reset my password."),
                new Turn("assistant", "You are all set."));
        var golden = ConversationalGolden.builder("A user needs account help.")
                .expectedOutcome("The user has reset their password.")
                .userDescription("Pat")
                .turns(initialTurns)
                .build();

        var testCases = simulator.simulate(List.of(golden), 5);

        assertEquals(initialTurns, testCases.getFirst().turns());
        assertEquals(0, callbacks.get());
        assertEquals(1, model.prompts().size());
        assertTrue(model.prompts().getFirst().contains("Conversation Completion Checker"));
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
