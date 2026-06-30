package dev.jeval.simulator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalGolden;
import dev.jeval.Turn;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulatorTemplateTest {

    @Test
    void firstUserTurnPromptMatchesDeepEvalContract() {
        var golden = ConversationalGolden.builder("A customer needs help with a delayed refund.")
                .userDescription("Maya, a premium customer")
                .build();

        var prompt = SimulationTemplate.simulateFirstUserTurn(golden, "spanish");

        assertTrue(prompt.contains("Pretend you are a user of an LLM app"));
        assertTrue(prompt.contains("start a conversation in spanish"));
        assertTrue(prompt.contains("Maya, a premium customer"));
        assertTrue(prompt.contains("A customer needs help with a delayed refund."));
        assertTrue(prompt.contains("simulated_input"));
        assertTrue(prompt.contains("--- MULTIMODAL INPUT RULES ---"));
    }

    @Test
    void nextUserTurnPromptIncludesConversationHistoryLikeDeepEval() {
        var golden = ConversationalGolden.builder("A user wants to reschedule an appointment.")
                .userDescription("Jordan, available after 3pm")
                .build();
        var turns = List.of(
                new Turn("user", "Can I move my appointment?"),
                new Turn("assistant", "Sure, what time works?"));

        var prompt = SimulationTemplate.simulateUserTurn(golden, turns, "english");

        assertTrue(prompt.contains("generate the next user input in english"));
        assertTrue(prompt.contains("Jordan, available after 3pm"));
        assertTrue(prompt.contains("A user wants to reschedule an appointment."));
        assertTrue(prompt.contains("Previous Conversation:"));
        assertTrue(prompt.contains("\"role\" : \"assistant\""));
        assertTrue(prompt.contains("\"content\" : \"Sure, what time works?\""));
        assertTrue(prompt.contains("simulated_input"));
    }

    @Test
    void controllerPromptChecksExpectedOutcomeLikeDeepEval() {
        var prompt = SimulatorControllerTemplate.checkExpectedOutcome(
                """
                        [
                          {"role":"user","content":"I reset my password."}
                        ]
                        """,
                "The user has successfully reset their password.");

        assertTrue(prompt.contains("You are a Conversation Completion Checker."));
        assertTrue(prompt.contains("is_complete"));
        assertTrue(prompt.contains("reason"));
        assertTrue(prompt.contains("The user has successfully reset their password."));
        assertTrue(prompt.contains("\"content\":\"I reset my password.\""));
    }

    @Test
    void graphPromptNumbersChoicesAndAddsNoneOptionLikeDeepEval() {
        var prompt = SimulationGraphTemplate.classifyEdge(
                "Your refund has been approved.",
                List.of("assistant approved the refund", "assistant denied the refund"));

        assertTrue(prompt.contains("You are routing a simulated conversation."));
        assertTrue(prompt.contains("Your refund has been approved."));
        assertTrue(prompt.contains("  1) assistant approved the refund"));
        assertTrue(prompt.contains("  2) assistant denied the refund"));
        assertTrue(prompt.contains("  3) None of the above"));
        assertTrue(prompt.contains("\"index\": null"));
        assertTrue(prompt.contains("option 3"));
    }
}
