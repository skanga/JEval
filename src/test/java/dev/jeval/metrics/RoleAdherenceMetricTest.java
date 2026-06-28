package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.Turn;
import dev.jeval.metrics.RoleAdherenceSchemas.OutOfCharacterResponseVerdict;
import dev.jeval.metrics.RoleAdherenceSchemas.OutOfCharacterResponseVerdicts;
import dev.jeval.metrics.RoleAdherenceSchemas.RoleAdherenceScoreReason;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleAdherenceMetricTest {

    @Test
    void measureScoresOutOfCharacterAssistantResponsesAndGeneratesReason() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"index\":3,\"reason\":\"Claims unmatched greatness.\"}]}",
                "{\"reason\":\"One assistant response broke the humble wizard role.\"}"));
        var metric = new RoleAdherenceMetric(model);

        var result = metric.measure(testCase("A humble wizard"));

        assertAll(
                () -> assertEquals("Role Adherence", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("One assistant response broke the humble wizard role.", result.reason()),
                () -> assertEquals(1, metric.outOfCharacterVerdicts().verdicts().size()),
                () -> assertEquals("I am the greatest wizard ever. (turn #4)",
                        metric.outOfCharacterVerdicts().verdicts().getFirst().aiMessage()),
                () -> assertTrue(model.prompts().get(0).contains("specify which `ai_message` did not adhere")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("Example Chatbot Role")),
                () -> assertTrue(model.prompts().get(0).contains("Example Messages")),
                () -> assertTrue(model.prompts().get(0).contains("I'll make the entire town disappear in an instant")),
                () -> assertTrue(model.prompts().get(0).contains("===== END OF EXAMPLE ======")),
                () -> assertTrue(model.prompts().get(0).contains("drastically deviates from the character's humble nature")),
                () -> assertTrue(model.prompts().get(0).contains("A humble wizard")),
                () -> assertTrue(model.prompts().get(0).contains("I am the greatest wizard ever.")),
                () -> assertTrue(model.prompts().get(1).contains("minimal knowledge")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("<role_adherence_score>")),
                () -> assertTrue(model.prompts().get(1).contains("Always cite information")),
                () -> assertTrue(model.prompts().get(1).contains("LLM chatbot responses")),
                () -> assertTrue(model.prompts().get(1).contains("0.5")),
                () -> assertTrue(model.prompts().get(1).contains("I am the greatest wizard ever. (turn #4)")));
    }

    @Test
    void includeReasonFalseSkipsReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[]}"));
        var metric = new RoleAdherenceMetric(model, 0.5, false, false);

        var result = metric.measure(testCase("A humble wizard"));

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(1, model.prompts().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubRoleAdherenceMetric(
                new OutOfCharacterResponseVerdicts(List.of(
                        new OutOfCharacterResponseVerdict(1, "Out of role.", null))),
                new RoleAdherenceScoreReason("Broke role."),
                true);

        var result = metric.measure(testCase("A humble wizard"));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureRequiresChatbotRole() {
        var metric = new StubRoleAdherenceMetric(
                new OutOfCharacterResponseVerdicts(List.of()),
                new RoleAdherenceScoreReason("Fine."),
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(testCase(null)));
    }

    private static ConversationalTestCase testCase(String chatbotRole) {
        return ConversationalTestCase.builder(List.of(
                        new Turn("user", "Show me magic."),
                        new Turn("assistant", "My magic is modest and uncertain."),
                        new Turn("user", "Try again."),
                        new Turn("assistant", "I am the greatest wizard ever.")))
                .chatbotRole(chatbotRole)
                .build();
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        ScriptedModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }

        List<String> prompts() {
            return prompts;
        }
    }

    private static final class StubRoleAdherenceMetric extends RoleAdherenceMetric {
        private final OutOfCharacterResponseVerdicts verdicts;
        private final RoleAdherenceScoreReason reason;

        StubRoleAdherenceMetric(
                OutOfCharacterResponseVerdicts verdicts,
                RoleAdherenceScoreReason reason,
                boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
            this.reason = reason;
        }

        @Override
        protected OutOfCharacterResponseVerdicts extractOutOfCharacterVerdicts(List<Turn> turns, String role) {
            return verdicts;
        }

        @Override
        protected RoleAdherenceScoreReason generateReason(String role) {
            return reason;
        }
    }
}
