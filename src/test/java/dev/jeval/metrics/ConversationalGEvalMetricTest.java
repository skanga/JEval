package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConversationalGEvalMetricTest {

    @Test
    void measureGeneratesStepsAndNormalizesScore() {
        var model = new ScriptedModel(List.of(
                "{\"steps\":[\"Check whether the assistant resolved the scenario.\"]}",
                "{\"score\":8,\"reason\":\"The conversation is mostly resolved.\"}"));
        var metric = new ConversationalGEvalMetric(
                model,
                "Resolution",
                List.of(MultiTurnParam.SCENARIO),
                "Judge whether the conversation resolved the user need.");

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Resolution [Conversational GEval]", result.name()),
                () -> assertEquals(0.8, result.score()),
                () -> assertEquals("The conversation is mostly resolved.", result.reason()),
                () -> assertTrue(result.success()),
                () -> assertEquals(List.of("Check whether the assistant resolved the scenario."), metric.evaluationSteps()),
                () -> assertTrue(model.prompts().getFirst().contains("Judge whether")),
                () -> assertTrue(model.prompts().getFirst().contains("turn-level fields")),
                () -> assertTrue(model.prompts().getFirst().contains("conversation-level fields")),
                () -> assertTrue(model.prompts().getFirst().contains("\"steps\" key as a list of strings")),
                () -> assertTrue(model.prompts().get(1).contains("conversation between a user and an LLM chatbot")),
                () -> assertTrue(model.prompts().get(1).contains("10 = The conversation *fully* meets")),
                () -> assertTrue(model.prompts().get(1).contains("0 = The conversation *completely fails*")),
                () -> assertTrue(model.prompts().get(1).contains("Per-turn fields")),
                () -> assertTrue(model.prompts().get(1).contains("exact keys \"score\" and \"reason\"")),
                () -> assertTrue(model.prompts().get(1).contains("Support refund request.")),
                () -> assertTrue(model.prompts().get(1).contains("I can refund that order.")),
                () -> assertTrue(model.prompts().get(1).contains("scenario")));
    }

    @Test
    void suppliedStepsSkipStepGeneration() {
        var model = new ScriptedModel(List.of("{\"score\":5,\"reason\":\"Partially aligned.\"}"));
        var metric = new ConversationalGEvalMetric(
                model,
                "Resolution",
                List.of(MultiTurnParam.CONTENT),
                null,
                List.of("Check the turn content."),
                null,
                0.5,
                false);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertEquals(1, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("Check the turn content.")));
    }

    @Test
    void strictModeUsesBinaryScoreAndThreshold() {
        var model = new ScriptedModel(List.of("{\"score\":0,\"reason\":\"Failed.\"}"));
        var metric = new ConversationalGEvalMetric(
                model,
                "Binary",
                List.of(MultiTurnParam.CONTENT),
                null,
                List.of("Return binary pass."),
                null,
                0.5,
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals(1.0, result.threshold()),
                () -> assertFalse(result.success()));
    }

    @Test
    void measureRequiresConfiguredConversationParams() {
        var metric = new ConversationalGEvalMetric(
                new ScriptedModel(List.of("{\"score\":1,\"reason\":\"ok\"}")),
                "Scenario",
                List.of(MultiTurnParam.SCENARIO),
                null,
                List.of("Check scenario."),
                null,
                0.5,
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(ConversationalTestCase.builder(List.of(new Turn("user", "Hi"))).build()));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                        new Turn("user", "I need a refund."),
                        new Turn("assistant", "I can refund that order.")))
                .scenario("Support refund request.")
                .metadata(Map.of("channel", "chat"))
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
}
