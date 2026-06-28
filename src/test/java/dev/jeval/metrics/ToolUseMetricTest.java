package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.ToolCall;
import dev.jeval.Turn;
import dev.jeval.metrics.ToolUseSchemas.ArgumentCorrectnessScore;
import dev.jeval.metrics.ToolUseSchemas.Reason;
import dev.jeval.metrics.ToolUseSchemas.ToolSelectionScore;
import dev.jeval.metrics.ToolUseSchemas.UserInputAndTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolUseMetricTest {

    @Test
    void measureFormatsInteractionsScoresSelectionAndArgumentsAndGeneratesReasons() {
        var model = new ScriptedModel(List.of(
                "{\"score\":1.0,\"reason\":\"weather_search was appropriate.\"}",
                "{\"score\":0.5,\"reason\":\"units argument was missing.\"}",
                "{\"reason\":\"Tool selection was correct.\"}",
                "{\"reason\":\"Tool arguments were incomplete.\"}"));
        var metric = new ToolUseMetric(List.of(new ToolCall("weather_search")), model);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals("Tool Use", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Tool selection was correct.\nTool arguments were incomplete.", result.reason()),
                () -> assertEquals(1, metric.userInputAndTools().size()),
                () -> assertEquals(1, metric.toolSelectionScores().size()),
                () -> assertEquals(1, metric.argumentCorrectnessScores().size()),
                () -> assertTrue(model.prompts().get(0).contains("Tool Selection Quality")),
                () -> assertTrue(model.prompts().get(0).contains("selected the most appropriate tools")),
                () -> assertTrue(model.prompts().get(0).contains("Return a JSON object with")),
                () -> assertTrue(model.prompts().get(0).contains("\"score\": float between 0.0 and 1.0")),
                () -> assertTrue(model.prompts().get(0).contains("referencing specific tool names")),
                () -> assertTrue(model.prompts().get(0).contains("weather in Paris")),
                () -> assertTrue(model.prompts().get(0).contains("weather_search")),
                () -> assertTrue(model.prompts().get(1).contains("Tool Argument Quality")),
                () -> assertTrue(model.prompts().get(1).contains("arguments and parameters")),
                () -> assertTrue(model.prompts().get(1).contains("Return a JSON object with")),
                () -> assertTrue(model.prompts().get(1).contains("\"score\": float between 0.0 and 1.0")),
                () -> assertTrue(model.prompts().get(1).contains("referencing specific parameter names")),
                () -> assertTrue(model.prompts().get(1).contains("\"location\": \"Paris\"")),
                () -> assertTrue(model.prompts().get(2).contains("Tool Selection")),
                () -> assertTrue(model.prompts().get(2).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains("Result: PASS")),
                () -> assertTrue(model.prompts().get(2).contains("weather_search was appropriate")),
                () -> assertTrue(model.prompts().get(3).contains("Tool Argument Quality")),
                () -> assertTrue(model.prompts().get(3).contains("with the 'reason' key")),
                () -> assertTrue(model.prompts().get(3).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(3).contains("Result: PASS")),
                () -> assertTrue(model.prompts().get(3).contains("units argument was missing")));
    }

    @Test
    void includeReasonFalseSkipsFinalReasonGeneration() {
        var model = new ScriptedModel(List.of(
                "{\"score\":1.0,\"reason\":\"Selected correctly.\"}",
                "{\"score\":1.0,\"reason\":\"Arguments correct.\"}"));
        var metric = new ToolUseMetric(List.of(new ToolCall("weather_search")), model, 0.5, false, false);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()),
                () -> assertNull(result.reason()),
                () -> assertEquals(2, model.prompts().size()));
    }

    @Test
    void noToolCallsUsesToolSelectionOnlyAndZeroArgumentAverage() {
        var model = new ScriptedModel(List.of(
                "{\"score\":1.0,\"reason\":\"No tool was needed.\"}",
                "{\"reason\":\"No tool selection issue.\"}",
                "{\"reason\":\"No argument checks were needed.\"}"));
        var metric = new ToolUseMetric(List.of(new ToolCall("weather_search")), model);

        var result = metric.measure(testCase(false));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(0, metric.argumentCorrectnessScores().size()));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubToolUseMetric(
                List.of(new UserInputAndTools("user", "assistant", "tool", "available", true)),
                List.of(new ToolSelectionScore(0.5, "Weak tool choice.")),
                List.of(new ArgumentCorrectnessScore(0.5, "Weak args.")),
                new Reason("Weak tool choice."),
                new Reason("Weak args."),
                true);

        var result = metric.measure(testCase(true));

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    private static ConversationalTestCase testCase(boolean withTool) {
        var assistant = withTool
                ? Turn.builder("assistant", "I checked the forecast.")
                        .toolsCalled(List.of(new ToolCall(
                                "weather_search",
                                Map.of("location", "Paris"),
                                "sunny")))
                        .build()
                : new Turn("assistant", "It is sunny.");
        return ConversationalTestCase.builder(List.of(
                new Turn("user", "weather in Paris"),
                assistant)).build();
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

    private static final class StubToolUseMetric extends ToolUseMetric {
        private final List<UserInputAndTools> userInputAndTools;
        private final List<ToolSelectionScore> toolSelectionScores;
        private final List<ArgumentCorrectnessScore> argumentCorrectnessScores;
        private final Reason toolReason;
        private final Reason argumentReason;

        StubToolUseMetric(
                List<UserInputAndTools> userInputAndTools,
                List<ToolSelectionScore> toolSelectionScores,
                List<ArgumentCorrectnessScore> argumentCorrectnessScores,
                Reason toolReason,
                Reason argumentReason,
                boolean strictMode) {
            super(List.of(new ToolCall("weather_search")), 0.5, true, strictMode);
            this.userInputAndTools = userInputAndTools;
            this.toolSelectionScores = toolSelectionScores;
            this.argumentCorrectnessScores = argumentCorrectnessScores;
            this.toolReason = toolReason;
            this.argumentReason = argumentReason;
        }

        @Override
        protected List<UserInputAndTools> getUserInputAndTools(List<List<Turn>> unitInteractions) {
            return userInputAndTools;
        }

        @Override
        protected ToolSelectionScore getToolSelectionScore(UserInputAndTools userInputAndTools) {
            return toolSelectionScores.getFirst();
        }

        @Override
        protected ArgumentCorrectnessScore getArgumentCorrectnessScore(UserInputAndTools userInputAndTools) {
            return argumentCorrectnessScores.getFirst();
        }

        @Override
        protected Reason generateToolSelectionReason() {
            return toolReason;
        }

        @Override
        protected Reason generateArgumentCorrectnessReason() {
            return argumentReason;
        }
    }
}
