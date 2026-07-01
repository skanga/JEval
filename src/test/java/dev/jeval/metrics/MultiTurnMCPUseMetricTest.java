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
import dev.jeval.metrics.MCPTaskCompletionMetric.Task;
import dev.jeval.metrics.MCPUseSchemas.MCPArgsScore;
import dev.jeval.metrics.MCPUseSchemas.MCPPrimitivesScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiTurnMCPUseMetricTest {

    @Test
    void measureUsesMinimumOfAveragePrimitiveAndArgumentScores() {
        var metric = new StubMultiTurnMCPUseMetric(
                List.of(new MCPPrimitivesScore(1.0, "Right tool.")),
                List.of(new MCPArgsScore(0.5, "Weak args.")));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("Multi-Turn MCP Use", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("Combined MCP reason.", result.reason()),
                () -> assertEquals(1, metric.tasks().size()),
                () -> assertTrue(metric.tasks().getFirst().task().contains("Find policy")),
                () -> assertTrue(metric.tasks().getFirst().stepsTaken().getFirst().contains("mcp-search")),
                () -> assertEquals(1, metric.primitivesScores().size()),
                () -> assertEquals(1, metric.argsScores().size()));
    }

    @Test
    void requiresMcpServersAndStrictModeZerosBelowThreshold() {
        var strict = new StubMultiTurnMCPUseMetric(
                List.of(new MCPPrimitivesScore(0.5, "Weak tool.")),
                List.of(new MCPArgsScore(0.5, "Weak args.")),
                true);

        assertThrows(MissingTestCaseParamsException.class,
                () -> strict.measure(ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        new Turn("assistant", "Policy found"))).build()));

        assertAll(
                () -> assertEquals(0.0, strict.measure(testCase()).score()),
                () -> assertFalse(strict.success()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MultiTurnMCPUseMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MultiTurnMCPUseMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"score\":0.75,\"reason\":\"Right primitive.\"}",
                "{\"score\":0.25,\"reason\":\"Wrong query.\"}",
                "{\"reason\":\"Arguments need work.\"}"));
        var metric = new MultiTurnMCPUseMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.25, result.score()),
                () -> assertEquals("Arguments need work.", result.reason()),
                () -> assertTrue(model.prompts().get(0).contains("Evaluate whether the tools, resources, and prompts used")),
                () -> assertTrue(model.prompts().get(0).contains("Available Tools:")),
                () -> assertTrue(model.prompts().get(0).contains("MCP Server policy\n\nAvailable Tools:\n[")),
                () -> assertFalse(model.prompts().get(0).contains("Available Resources:")),
                () -> assertFalse(model.prompts().get(0).contains("Available Prompts:")),
                () -> assertFalse(model.prompts().get(0).contains("MCP Primitives Available")),
                () -> assertFalse(model.prompts().get(0).contains("server_name=policy")),
                () -> assertTrue(model.prompts().get(0).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(0).contains("mcp-search")),
                () -> assertTrue(model.prompts().get(0).contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts().get(0).contains("Example Output")),
                () -> assertTrue(model.prompts().get(1).contains("Evaluate whether the arguments (inputs) provided")),
                () -> assertTrue(model.prompts().get(1).contains("Input Schemas:")),
                () -> assertTrue(model.prompts().get(1).contains("MCP Server policy\n\nAvailable Tools:\n[")),
                () -> assertTrue(model.prompts().get(1).contains("MCP Server policy\n\nAvailable Resources:\n[")),
                () -> assertTrue(model.prompts().get(1).contains("MCP Server policy\n\nAvailable Prompts:\n[")),
                () -> assertTrue(model.prompts().get(1).contains("Available Resources:\n[")),
                () -> assertTrue(model.prompts().get(1).contains("Available Prompts:\n[")),
                () -> assertFalse(model.prompts().get(1).contains("MCP Primitives Available")),
                () -> assertFalse(model.prompts().get(1).contains("server_name=policy")),
                () -> assertTrue(model.prompts().get(1).contains("Find policy")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts().get(1).contains("Example Output")),
                () -> assertTrue(model.prompts().get(2).contains("Example JSON")),
                () -> assertTrue(model.prompts().get(2).contains(
                        "determining whether the model accurately completed a task or called tools and resources with the right arguments")),
                () -> assertTrue(model.prompts().get(2).contains("single paragraph with no lists")),
                () -> assertTrue(model.prompts().get(2).contains("Output ONLY the reason")),
                () -> assertTrue(model.prompts().get(2).contains("Final Score: 0.25")),
                () -> assertTrue(model.prompts().get(2).contains("Right primitive.")),
                () -> assertTrue(model.prompts().get(2).contains("Wrong query.")),
                () -> assertTrue(model.prompts().get(2).contains("Success: false")));
    }

    @Test
    void formatsMcpInteractionsLikeDeepEval() {
        var metric = new StubMultiTurnMCPUseMetric(
                List.of(new MCPPrimitivesScore(1.0, "Right tool.")),
                List.of(new MCPArgsScore(1.0, "Right args.")));
        var testCase = ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of(
                                        "name", "search",
                                        "args", Map.of("query", "policy"),
                                        "result", "policy-result")))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of("server_name", "policy")))
                .build();

        metric.measure(testCase);

        assertTrue(metric.tasks().getFirst().stepsTaken().getFirst().contains("""
                Name: search
                Args: {query=policy}
                Result:\s
                policy-result
                """));
    }

    private static ConversationalTestCase testCase() {
        return ConversationalTestCase.builder(List.of(
                        new Turn("user", "Find policy"),
                        Turn.builder("assistant", "Looking it up")
                                .mcpToolsCalled(List.of(Map.of(
                                        "name", "mcp-search",
                                        "arguments", Map.of("query", "policy"))))
                                .build(),
                        new Turn("assistant", "Policy found")))
                .mcpServers(List.of(Map.of(
                        "server_name", "policy",
                        "available_tools", List.of(Map.of("name", "mcp-search")),
                        "available_resources", List.of(Map.of("uri", "policy://handbook")),
                        "available_prompts", List.of(Map.of("name", "policy-summary")))))
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

    private static final class StubMultiTurnMCPUseMetric extends MultiTurnMCPUseMetric {
        private final List<MCPPrimitivesScore> primitivesScores;
        private final List<MCPArgsScore> argsScores;

        StubMultiTurnMCPUseMetric(List<MCPPrimitivesScore> primitivesScores, List<MCPArgsScore> argsScores) {
            this(primitivesScores, argsScores, false);
        }

        StubMultiTurnMCPUseMetric(
                List<MCPPrimitivesScore> primitivesScores,
                List<MCPArgsScore> argsScores,
                boolean strictMode) {
            super(0.5, true, strictMode);
            this.primitivesScores = primitivesScores;
            this.argsScores = argsScores;
        }

        @Override
        protected MCPPrimitivesScore getPrimitivesUsedScore(Task task, ConversationalTestCase testCase) {
            return primitivesScores.get(primitivesScores().size());
        }

        @Override
        protected MCPArgsScore getArgumentCorrectnessScore(Task task, ConversationalTestCase testCase) {
            return argsScores.get(argsScores().size());
        }

        @Override
        protected String generateReason() {
            return "Combined MCP reason.";
        }
    }
}
