package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ArenaTestCase;
import dev.jeval.Contestant;
import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.SingleTurnParam;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ArenaGEvalMetricTest {

    @Test
    void measureGeneratesStepsMapsMaskedWinnerAndRewritesReason() {
        var model = new ScriptedModel(List.of(
                "{\"steps\":[\"Compare each contestant response against the input.\"]}",
                "{\"winner\":\"Alice\",\"reason\":\"Alice gave the clearer answer.\"}",
                "{\"rewritten_reason\":\"model-a gave the clearer answer.\"}"));
        var metric = new ArenaGEvalMetric(
                model,
                "Preference",
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                "Pick the better answer.");

        var winner = metric.measure(arena(true));

        assertAll(
                () -> assertEquals("model-a", winner),
                () -> assertEquals("model-a", metric.winner()),
                () -> assertEquals("model-a gave the clearer answer.", metric.reason()),
                () -> assertTrue(metric.success()),
                () -> assertEquals(List.of("Compare each contestant response against the input."), metric.evaluationSteps()),
                () -> assertTrue(model.prompts().getFirst().contains("Pick the better answer.")),
                () -> assertTrue(model.prompts().getFirst().contains("choose the winner out of all contestants")),
                () -> assertTrue(model.prompts().getFirst().contains("relation to one another")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().getFirst().contains("with the \"steps\" key")),
                () -> assertTrue(model.prompts().getFirst().contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("You are a judge")),
                () -> assertTrue(model.prompts().get(1).contains("MUST NOT contain any symbols")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Alice")),
                () -> assertTrue(model.prompts().get(1).contains("Bob")),
                () -> assertTrue(model.prompts().get(1).contains("A concise answer.")),
                () -> assertTrue(model.prompts().get(2).contains("$name$ format")),
                () -> assertTrue(model.prompts().get(2).contains("Dummy-to-real mapping")),
                () -> assertTrue(model.prompts().get(2).contains("Alice gave the clearer answer.")),
                () -> assertTrue(model.prompts().get(2).contains("model-a")));
    }

    @Test
    void suppliedStepsSkipStepGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"winner\":\"Bob\",\"reason\":\"Bob is better.\"}",
                "{\"rewritten_reason\":\"model-b is better.\"}"));
        var metric = new ArenaGEvalMetric(
                model,
                "Preference",
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                null,
                List.of("Pick the clearer answer."));

        var winner = metric.measure(arena());

        assertAll(
                () -> assertEquals("model-b", winner),
                () -> assertEquals(2, model.prompts().size()),
                () -> assertTrue(model.prompts().getFirst().contains("Pick the clearer answer.")));
    }

    @Test
    void comparisonPromptFormatsArenaTestCaseAsJson() {
        var model = new ScriptedModel(List.of(
                "{\"winner\":\"Alice\",\"reason\":\"Alice is better.\"}",
                "{\"rewritten_reason\":\"model-a is better.\"}"));
        var metric = new ArenaGEvalMetric(
                model,
                "Preference",
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                null,
                List.of("Pick the clearer answer."));

        metric.measure(arena());

        var prompt = model.prompts().getFirst();
        assertAll(
                () -> assertTrue(prompt.contains("\"arena_test_cases\"")),
                () -> assertTrue(prompt.contains("\"actual_output\"")),
                () -> assertFalse(prompt.contains("arena_test_cases={")),
                () -> assertFalse(prompt.contains("actual_output=")));
    }

    @Test
    void asyncMeasureMatchesSynchronousArenaMetricBehaviorLikeDeepEval() throws Exception {
        var metric = new ArenaGEvalMetric(
                new ScriptedModel(List.of(
                        "{\"winner\":\"Alice\",\"reason\":\"Alice is better.\"}",
                        "{\"rewritten_reason\":\"model-a is better.\"}")),
                "Preference",
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                null,
                List.of("Pick the clearer answer."));

        var winner = metric.aMeasure(arena()).get(5, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals("model-a", winner),
                () -> assertEquals("model-a", metric.winner()),
                () -> assertTrue(metric.success()));
    }

    @Test
    void measureRequiresSelectedContestantParams() {
        var metric = new ArenaGEvalMetric(
                new ScriptedModel(List.of("{\"winner\":\"Alice\",\"reason\":\"ok\"}")),
                "Preference",
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                null,
                List.of("Pick one."));
        var arena = new ArenaTestCase(List.of(
                new Contestant("model-a", LlmTestCase.builder("input").actualOutput("answer").build()),
                new Contestant("model-b", LlmTestCase.builder("input").build())));

        assertThrows(MissingTestCaseParamsException.class, () -> metric.measure(arena));
    }

    private static ArenaTestCase arena() {
        return arena(false);
    }

    private static ArenaTestCase arena(boolean multimodal) {
        return new ArenaTestCase(List.of(
                new Contestant("model-a", LlmTestCase.builder("input")
                        .actualOutput("A concise answer.")
                        .expectedOutput("expected")
                        .multimodal(multimodal)
                        .build()),
                new Contestant("model-b", LlmTestCase.builder("input")
                        .actualOutput("A verbose answer.")
                        .expectedOutput("expected")
                        .build())));
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
