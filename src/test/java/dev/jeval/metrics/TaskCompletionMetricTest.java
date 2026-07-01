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
import dev.jeval.metrics.TaskCompletionSchemas.TaskAndOutcome;
import dev.jeval.metrics.TaskCompletionSchemas.TaskCompletionVerdict;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskCompletionMetricTest {

    @Test
    void measureUsesExtractedTaskOutcomeAndVerdictScore() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Plan a trip\",\"outcome\":\"Suggested flights and hotels.\"}",
                "{\"verdict\":0.75,\"reason\":\"Missing sightseeing.\"}"));
        var metric = new TaskCompletionMetric(model);
        var testCase = LlmTestCase.builder("Plan my NYC trip")
                .actualOutput("Flights and hotels.")
                .toolsCalled(List.of(new ToolCall("flight_search")))
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("Task Completion", result.name()),
                () -> assertEquals(0.75, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Missing sightseeing.", result.reason()),
                () -> assertEquals("Plan a trip", metric.task()),
                () -> assertEquals("Suggested flights and hotels.", metric.outcome()),
                () -> assertTrue(model.prompts().get(0).contains("identify the task (or objective the user wants to achieve)")),
                () -> assertTrue(model.prompts().get(0).contains("task_outcome")),
                () -> assertTrue(model.prompts().get(0).contains("Example input")),
                () -> assertTrue(model.prompts().get(0).contains("Example tools called")),
                () -> assertTrue(model.prompts().get(0).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(0).contains(
                        "suggested flights departing on Saturday and returning on Sunday")),
                () -> assertTrue(model.prompts().get(0).contains("===== END OF EXAMPLE ======")),
                () -> assertTrue(model.prompts().get(0).contains("Plan my NYC trip")),
                () -> assertTrue(model.prompts().get(0).contains("flight_search")),
                () -> assertTrue(model.prompts().get(1).contains("Please return a JSON with two keys: `verdict` and `reason`")),
                () -> assertTrue(model.prompts().get(1).contains("IMPORTANT: Please make sure to only return in JSON format")),
                () -> assertTrue(model.prompts().get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(1).contains(
                        "identified hotels with check-in on Saturday and check-out on Sunday")),
                () -> assertTrue(model.prompts().get(1).contains("\"verdict\": 0.85")),
                () -> assertTrue(model.prompts().get(1).contains("Plan a trip")),
                () -> assertTrue(model.prompts().get(1).contains("Suggested flights and hotels.")));
    }

    @Test
    void providedTaskOverridesExtractedTask() {
        var model = new ScriptedModel(List.of(
                "{\"task\":\"Ignored\",\"outcome\":\"Done.\"}",
                "{\"verdict\":1.0,\"reason\":\"Complete.\"}"));
        var metric = new TaskCompletionMetric(model, 0.5, "Book hotel", true, false);

        metric.measure(LlmTestCase.builder("Need lodging").actualOutput("Booked it.").build());

        assertAll(
                () -> assertEquals("Book hotel", metric.task()),
                () -> assertEquals("Done.", metric.outcome()),
                () -> assertTrue(model.prompts().get(1).contains("Book hotel")));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubTaskCompletionMetric(
                new TaskAndOutcome("Do task", "Partially done."),
                new TaskCompletionVerdict(0.5, "Partial."),
                true);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TaskCompletionMetric(Double.NaN, null, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TaskCompletionMetric(Double.POSITIVE_INFINITY, null, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubTaskCompletionMetric(
                new TaskAndOutcome("Task", "Outcome"),
                new TaskCompletionVerdict(1.0, "Done."),
                false);

        var result = metric.measure(LlmTestCase.builder("").actualOutput("output").build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutput() {
        var metric = new StubTaskCompletionMetric(
                new TaskAndOutcome("Task", "Outcome"),
                new TaskCompletionVerdict(1.0, "Done."),
                false);

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").build()));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("input").actualOutput("output").build();
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

    private static final class StubTaskCompletionMetric extends TaskCompletionMetric {
        private final TaskAndOutcome taskAndOutcome;
        private final TaskCompletionVerdict verdict;

        StubTaskCompletionMetric(
                TaskAndOutcome taskAndOutcome,
                TaskCompletionVerdict verdict,
                boolean strictMode) {
            super(0.5, null, true, strictMode);
            this.taskAndOutcome = taskAndOutcome;
            this.verdict = verdict;
        }

        @Override
        protected TaskAndOutcome extractTaskAndOutcome(LlmTestCase testCase) {
            return taskAndOutcome;
        }

        @Override
        protected TaskCompletionVerdict generateVerdict() {
            return verdict;
        }
    }
}
