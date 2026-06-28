package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.ToolCall;
import dev.jeval.metrics.ArgumentCorrectnessSchemas.ArgumentCorrectnessVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ArgumentCorrectnessMetricTest {

    @Test
    void measureScoresNonNoVerdictsAsCorrect() {
        var metric = new StubArgumentCorrectnessMetric(List.of(
                new ArgumentCorrectnessVerdict("yes", null),
                new ArgumentCorrectnessVerdict("no", "Wrong city."),
                new ArgumentCorrectnessVerdict("idk", null)));

        var result = metric.measure(testCase(List.of(new ToolCall("weather"))));

        assertAll(
                () -> assertEquals("Argument Correctness", result.name()),
                () -> assertEquals(2.0 / 3.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("reason", result.reason()),
                () -> assertEquals(3, metric.verdicts().size()));
    }

    @Test
    void emptyToolCallsReturnPerfectScore() {
        var result = new ArgumentCorrectnessMetric()
                .measure(testCase(List.of()));

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("No tool calls provided", result.reason()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubArgumentCorrectnessMetric(List.of(
                new ArgumentCorrectnessVerdict("yes", null),
                new ArgumentCorrectnessVerdict("no", "Wrong city.")),
                true);

        var result = metric.measure(testCase(List.of(new ToolCall("weather"))));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubArgumentCorrectnessMetric(List.of());

        var result = metric.measure(testCase("", List.of(new ToolCall("weather"))));

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresToolsCalled() {
        var metric = new StubArgumentCorrectnessMetric(List.of());

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"verdicts\":[{\"verdict\":\"no\",\"reason\":\"Wrong city.\"}]}",
                "{\"reason\":\"The tool used the wrong city.\"}"));
        var metric = new ArgumentCorrectnessMetric(model);

        var result = metric.measure(LlmTestCase.builder("What was the highest temperature in Paris?")
                .toolsCalled(List.of(new ToolCall("weather", Map.of("city", "Boston"), null)))
                .multimodal(true)
                .build());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertEquals("The tool used the wrong city.", result.reason()),
                () -> assertTrue(model.prompts().getFirst().contains("valid and parseable JSON")),
                () -> assertTrue(model.prompts().getFirst().contains("with the 'verdicts' key")),
                () -> assertTrue(model.prompts().getFirst().contains("Example tool calls")),
                () -> assertTrue(model.prompts().getFirst().contains("Example JSON")),
                () -> assertTrue(model.prompts().getFirst().contains("No input parameter provided")),
                () -> assertTrue(model.prompts().getFirst().contains("weather")),
                () -> assertTrue(model.prompts().getFirst().contains("highest temperature")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("why it is not higher")),
                () -> assertTrue(model.prompts().get(1).contains("do not mention an output or a response")),
                () -> assertTrue(model.prompts().get(1).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains("<argument_correctness_score>")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("Wrong city.")));
    }

    private static LlmTestCase testCase(List<ToolCall> toolsCalled) {
        return testCase("What was the highest temperature in Paris?", toolsCalled);
    }

    private static LlmTestCase testCase(String input, List<ToolCall> toolsCalled) {
        return LlmTestCase.builder(input).toolsCalled(toolsCalled).build();
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

    private static final class StubArgumentCorrectnessMetric extends ArgumentCorrectnessMetric {
        private final List<ArgumentCorrectnessVerdict> verdicts;

        StubArgumentCorrectnessMetric(List<ArgumentCorrectnessVerdict> verdicts) {
            this(verdicts, false);
        }

        StubArgumentCorrectnessMetric(List<ArgumentCorrectnessVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected List<ArgumentCorrectnessVerdict> generateVerdicts(
                String input,
                List<ToolCall> toolsCalled,
                boolean multimodal) {
            return verdicts;
        }

        @Override
        protected String generateReason(String input, boolean multimodal) {
            return "reason";
        }
    }
}
