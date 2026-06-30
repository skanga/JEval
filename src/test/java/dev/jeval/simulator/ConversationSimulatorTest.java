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
    void generateSchemaExtractsEmbeddedJsonLikeDeepEval() {
        var model = new ScriptedModel(List.of("""
                Here is the simulated input:
                ```json
                {"simulated_input":"I need help with my order",}
                ```
                """));
        var simulator = new ConversationSimulator(input -> new Turn("assistant", "ok"), model);

        var input = simulator.generateFirstUserInput(ConversationalGolden.builder("order support").build());

        assertEquals("I need help with my order", input);
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

    @Test
    void simulateUsesCustomSimulationGraphLikeDeepEval() {
        var model = new ScriptedModel(List.of());
        var customGraph = SimulationNode.ofText(
                context -> "custom user turn for " + context.golden().scenario(),
                true,
                null,
                "custom");
        var simulator = new ConversationSimulator(
                context -> new Turn("assistant", "assistant reply"),
                model,
                "English",
                customGraph,
                SimulationController.custom(context -> SimulationController.proceed()));
        var golden = ConversationalGolden.builder("custom flow").build();

        var testCase = simulator.simulate(golden, 5);

        assertEquals(List.of(
                new Turn("user", "custom user turn for custom flow"),
                new Turn("assistant", "assistant reply")),
                testCase.turns());
        assertEquals(List.of(), model.prompts());
    }

    @Test
    void simulateUsesCustomStoppingControllerLikeDeepEval() {
        var model = new ScriptedModel(List.of("{\"simulated_input\":\"second question\"}"));
        var seenContexts = new ArrayList<SimulationContext>();
        var controller = SimulationController.custom(context -> {
            seenContexts.add(context);
            return context.lastAssistantTurn() == null
                    ? SimulationController.proceed()
                    : SimulationController.end("assistant already replied");
        });
        var callbacks = new AtomicInteger();
        var simulator = new ConversationSimulator(
                context -> {
                    callbacks.incrementAndGet();
                    return new Turn("assistant", "first answer");
                },
                model,
                "English",
                null,
                controller);
        var golden = ConversationalGolden.builder("support flow")
                .turns(List.of(new Turn("user", "first question")))
                .build();

        var testCase = simulator.simulate(golden, 5);

        assertEquals(List.of(
                new Turn("user", "first question"),
                new Turn("assistant", "first answer")),
                testCase.turns());
        assertEquals(1, callbacks.get());
        assertEquals(2, seenContexts.size());
        assertEquals(null, seenContexts.getFirst().lastAssistantTurn());
        assertEquals("first answer", seenContexts.get(1).lastAssistantTurn().content());
        assertEquals(List.of(), model.prompts());
    }

    @Test
    void customGraphAndControllerReceiveLanguageLikeDeepEval() {
        var model = new ScriptedModel(List.of());
        var controllerLanguages = new ArrayList<String>();
        var controller = SimulationController.custom(context -> {
            controllerLanguages.add(context.language());
            return SimulationController.proceed();
        });
        var graph = SimulationNode.ofText(
                context -> "Question in " + context.language(),
                true,
                null,
                "localized");
        var simulator = new ConversationSimulator(
                context -> new Turn("assistant", "answer"),
                model,
                "Spanish",
                graph,
                controller);

        var testCase = simulator.simulate(ConversationalGolden.builder("localized flow").build(), 3);

        assertEquals(List.of("Spanish"), controllerLanguages);
        assertEquals("Question in Spanish", testCase.turns().getFirst().content());
    }

    @Test
    void simulateDefaultsToTenUserSimulationsAndRetainsConversationsLikeDeepEval() {
        var responses = new ArrayList<String>();
        for (var index = 1; index <= 10; index++) {
            responses.add("{\"simulated_input\":\"question " + index + "\"}");
        }
        var model = new ScriptedModel(responses);
        var simulator = new ConversationSimulator(
                context -> new Turn("assistant", "answer to " + context.input()),
                model);
        var golden = ConversationalGolden.builder("long support flow").build();

        var testCases = simulator.simulate(List.of(golden));

        assertEquals(1, testCases.size());
        assertEquals(20, testCases.getFirst().turns().size());
        assertEquals("question 10", testCases.getFirst().turns().get(18).content());
        assertEquals(testCases, simulator.simulatedConversations());
    }

    @Test
    void simulateInvokesCompletionCallbackWithConversationAndIndexLikeDeepEval() {
        var model = new ScriptedModel(List.of(
                "{\"simulated_input\":\"first question\"}",
                "{\"simulated_input\":\"second question\"}"));
        var simulator = new ConversationSimulator(
                context -> new Turn("assistant", "answer to " + context.input()),
                model);
        var seenIndexes = new ArrayList<Integer>();
        var seenScenarios = new ArrayList<String>();
        var goldens = List.of(
                ConversationalGolden.builder("first flow").build(),
                ConversationalGolden.builder("second flow").build());

        var testCases = simulator.simulate(goldens, 1, (testCase, index) -> {
            seenIndexes.add(index);
            seenScenarios.add(testCase.scenario());
        });

        assertEquals(List.of(0, 1), seenIndexes);
        assertEquals(List.of("first flow", "second flow"), seenScenarios);
        assertEquals(testCases, simulator.simulatedConversations());
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
