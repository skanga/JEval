package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.Turn;
import dev.jeval.metrics.TaskCompletionSchemas.TaskCompletionVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MCPTaskCompletionMetricTest {

    @Test
    void measureAveragesTaskScoresAndReasons() {
        var metric = new StubMCPTaskCompletionMetric(List.of(
                new TaskCompletionVerdict(1.0, "First done."),
                new TaskCompletionVerdict(0.5, "Second partial.")));
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of("name", "search")))
                                .build(),
                        new Turn("assistant", "Policy found"),
                        new Turn("user", "Explain discount"),
                        new Turn("assistant", "Discount explained"),
                        new Turn("assistant", "Discount confirmed")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals("MCP Task Completion", result.name()),
                () -> assertEquals(0.75, result.score()),
                () -> assertEquals("First done.\nSecond partial.", result.reason()),
                () -> assertEquals(0.75, metric.score()),
                () -> assertEquals(2, metric.taskScores().size()));
    }

    @Test
    void measureRequiresMcpServersAndStrictModeZerosBelowThreshold() {
        var missingServers = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var strict = new StubMCPTaskCompletionMetric(List.of(new TaskCompletionVerdict(0.5, "Partial.")), true);
        var valid = ConversationalTestCase.builder(List.of(
                        new Turn("user", "hello"),
                        new Turn("assistant", "hi")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        assertThrows(MissingTestCaseParamsException.class, () -> strict.measure(missingServers));
        assertAll(
                () -> assertEquals(0.0, strict.measure(valid).score()),
                () -> assertFalse(strict.success()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MCPTaskCompletionMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MCPTaskCompletionMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void presentEmptyMcpListStillCountsAsMcpInteraction() {
        var metric = new StubMCPTaskCompletionMetric(List.of(new TaskCompletionVerdict(1.0, "Done.")));
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of())
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        metric.measure(testCase);

        assertEquals("Tools called by agent: \n", metric.tasks().getFirst().stepsTaken().getFirst());
    }

    @Test
    void formatsMcpInteractionsLikeDeepEval() {
        var metric = new StubMCPTaskCompletionMetric(List.of(new TaskCompletionVerdict(1.0, "Done.")));
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of(
                                        "name", "search",
                                        "args", Map.of("query", "policy"),
                                        "result", Map.of("structuredContent", Map.of("result", "policy-result")))))
                                .mcpResourcesCalled(List.of(Map.of(
                                        "uri", "file://policy",
                                        "result", "resource-result")))
                                .mcpPromptsCalled(List.of(Map.of(
                                        "name", "policy-prompt",
                                        "result", "prompt-result")))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        metric.measure(testCase);

        assertEquals("""
                Tools called by agent:\s

                <Tool Called>

                **This does not appear to user**
                Name: search
                Args: {query=policy}
                Result:\s
                policy-result
                </Tool Called>

                <Resource Called>

                **This does not appear to user**
                URI: file://policy
                Result: resource-result
                </Resource Called>

                <Prompt Called>

                **This does not appear to user**
                Name: policy-prompt
                Result: prompt-result
                </Prompt Called>
                """, metric.tasks().getFirst().stepsTaken().getFirst());
    }

    @Test
    void skipsTwoTurnInteractionsLikeDeepEval() {
        var metric = new StubMCPTaskCompletionMetric(List.of(new TaskCompletionVerdict(1.0, "Done.")));
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of("name", "search")))
                                .build()))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        metric.measure(testCase);

        assertEquals(List.of(), metric.tasks());
    }

    @Test
    void modelBackedMetricParsesDeepEvalScoreKeyAndBuildsTaskPrompt() {
        var model = new ScriptedModel(
                "{\"score\": 0.75, \"reason\": \"Visible answer was incomplete.\"}",
                "{\"reason\": \"The score is 0.75 because the visible answer was incomplete.\"}");
        var metric = new MCPTaskCompletionMetric(model);
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of("name", "search")))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        var result = metric.measure(testCase);

        assertAll(
                () -> assertEquals(0.75, result.score()),
                () -> assertEquals("The score is 0.75 because the visible answer was incomplete.", result.reason()),
                () -> assertEquals(2, model.prompts.size()),
                () -> assertTrue(model.prompts.getFirst().contains("Find policy")),
                () -> assertTrue(model.prompts.getFirst().contains("Tools called by agent")),
                () -> assertTrue(model.prompts.getFirst().contains("Agent's response to user")),
                () -> assertTrue(model.prompts.getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts.getFirst().contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts.getFirst().contains("fulfilled that part of the user's request *visibly*")),
                () -> assertTrue(model.prompts.getFirst().contains("Example Output")),
                () -> assertTrue(model.prompts.getFirst().contains("JSON:")),
                () -> assertTrue(model.prompts.get(1).contains("Example JSON")),
                () -> assertTrue(model.prompts.get(1).contains(
                        "determining whether the model accurately completed a task or called tools and resources with the right arguments")),
                () -> assertTrue(model.prompts.get(1).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts.get(1).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts.get(1).contains("Final Score: 0.75")),
                () -> assertTrue(model.prompts.get(1).contains("Visible answer was incomplete.")),
                () -> assertTrue(model.prompts.get(1).contains("Success: true")));
    }

    @Test
    void modelBackedMetricRejectsInvalidFinalReasonSchemaLikeDeepEval() {
        var model = new ScriptedModel(
                "{\"score\": 0.75, \"reason\": \"Visible answer was incomplete.\"}",
                "{\"score\": 0.75}");
        var metric = new MCPTaskCompletionMetric(model);
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of("name", "search")))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        assertThrows(IllegalArgumentException.class, () -> metric.measure(testCase));
    }

    @Test
    void modelBackedMetricRejectsInvalidTaskScoreSchemaLikeDeepEval() {
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of("name", "search")))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MCPTaskCompletionMetric(new ScriptedModel(
                                "{\"score\": 0.75}",
                                "{\"reason\": \"valid final reason\"}"))
                                .measure(testCase)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MCPTaskCompletionMetric(new ScriptedModel(
                                "{\"verdict\": 0.75, \"reason\": \"wrong key\"}",
                                "{\"reason\": \"valid final reason\"}"))
                                .measure(testCase)));
    }

    private static final class StubMCPTaskCompletionMetric extends MCPTaskCompletionMetric {
        private final List<TaskCompletionVerdict> verdicts;

        StubMCPTaskCompletionMetric(List<TaskCompletionVerdict> verdicts) {
            this(verdicts, false);
        }

        StubMCPTaskCompletionMetric(List<TaskCompletionVerdict> verdicts, boolean strictMode) {
            super(0.5, true, strictMode);
            this.verdicts = verdicts;
        }

        @Override
        protected TaskCompletionVerdict generateTaskScore(Task task) {
            return verdicts.get(taskScores().size());
        }
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        private ScriptedModel(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public String generate(String prompt) {
            prompts.add(prompt);
            return responses.get(prompts.size() - 1);
        }
    }
}
