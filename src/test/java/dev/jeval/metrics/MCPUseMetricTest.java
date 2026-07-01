package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.metrics.MCPUseSchemas.MCPArgsScore;
import dev.jeval.metrics.MCPUseSchemas.MCPPrimitivesScore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MCPUseMetricTest {

    @Test
    void measureUsesMinimumOfPrimitiveAndArgumentScores() {
        var metric = new StubMCPUseMetric(
                new MCPPrimitivesScore(0.75, "Good primitive."),
                new MCPArgsScore(0.5, "Weak args."));

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals("MCP Use", result.name()),
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertEquals("[\n\tGood primitive.\n\tWeak args.\n]\n", result.reason()),
                () -> assertTrue(metric.availablePrimitives().contains("weather")),
                () -> assertTrue(metric.primitivesUsed().contains("weather.search")));
    }

    @Test
    void formatsMcpInteractionBlocksLikeDeepEval() {
        var metric = new StubMCPUseMetric(
                new MCPPrimitivesScore(1.0, "Good primitive."),
                new MCPArgsScore(1.0, "Good args."));
        var server = new LinkedHashMap<String, Object>();
        server.put("server_name", "weather");
        server.put("available_tools", List.of(Map.of("name", "weather.search")));
        var toolCall = new LinkedHashMap<String, Object>();
        toolCall.put("name", "weather.search");
        toolCall.put("arguments", Map.of("city", "Paris"));
        var testCase = LlmTestCase.builder("weather in Paris")
                .actualOutput("It is sunny.")
                .mcpServers(List.of(server))
                .mcpToolsCalled(List.of(toolCall))
                .build();

        metric.measure(testCase);

        assertAll(
                () -> assertTrue(metric.availablePrimitives().contains("MCP Server weather")),
                () -> assertTrue(metric.availablePrimitives().contains("Available Tools:\n[\n")),
                () -> assertTrue(metric.availablePrimitives().contains("    {name=weather.search}")),
                () -> assertTrue(metric.primitivesUsed().contains("MCP Tools Called:\n[\n")),
                () -> assertTrue(metric.primitivesUsed().contains("    {name=weather.search, arguments={city=Paris}}")));
    }

    @Test
    void strictModeZerosScoresBelowThreshold() {
        var metric = new StubMCPUseMetric(
                new MCPPrimitivesScore(0.75, "Good primitive."),
                new MCPArgsScore(0.5, "Weak args."),
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
                        () -> new MCPUseMetric(Double.NaN, true, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new MCPUseMetric(Double.POSITIVE_INFINITY, true, false)));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var metric = new StubMCPUseMetric(new MCPPrimitivesScore(1.0, "ok"), new MCPArgsScore(1.0, "ok"));

        var result = metric.measure(LlmTestCase.builder("")
                .actualOutput("output")
                .mcpServers(List.of(Map.of()))
                .build());

        assertEquals(1.0, result.score());
    }

    @Test
    void measureRequiresActualOutputAndMcpServers() {
        var metric = new StubMCPUseMetric(new MCPPrimitivesScore(1.0, "ok"), new MCPArgsScore(1.0, "ok"));

        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("").mcpServers(List.of(Map.of())).build()));
        assertThrows(MissingTestCaseParamsException.class,
                () -> metric.measure(LlmTestCase.builder("input").actualOutput("output").build()));
    }

    @Test
    void measureUsesModelPromptsAndParsesJsonResponses() {
        var model = new ScriptedModel(List.of(
                "{\"score\":0.75,\"reason\":\"Right primitive.\"}",
                "{\"score\":0.25,\"reason\":\"Wrong city argument.\"}"));
        var metric = new MCPUseMetric(model);

        var result = metric.measure(testCase());

        assertAll(
                () -> assertEquals(0.25, result.score()),
                () -> assertEquals("[\n\tRight primitive.\n\tWrong city argument.\n]\n", result.reason()),
                () -> assertTrue(model.prompts().getFirst().contains("Evaluate whether the tools (primitives) selected")),
                () -> assertTrue(model.prompts().getFirst().contains("Available Tools:")),
                () -> assertTrue(model.prompts().getFirst().contains("Tools Used by Agent:")),
                () -> assertTrue(model.prompts().getFirst().contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().getFirst().contains("Chose the correct tool(s)")),
                () -> assertTrue(model.prompts().getFirst().contains("e.g., 0.25, 0.5, 0.75")),
                () -> assertTrue(model.prompts().getFirst().contains("CHAIN OF THOUGHT")),
                () -> assertTrue(model.prompts().getFirst().contains("right tool to use")),
                () -> assertTrue(model.prompts().getFirst().contains("Example Output")),
                () -> assertTrue(model.prompts().getFirst().contains("weather")),
                () -> assertTrue(model.prompts().getFirst().contains("weather.search")),
                () -> assertTrue(model.prompts().getFirst().contains("weather in Paris")),
                () -> assertTrue(model.prompts().get(1).contains("Available Primitives (with expected arguments and signatures):")),
                () -> assertTrue(model.prompts().get(1).contains("Primitives Used by Agent (with arguments passed):")),
                () -> assertTrue(model.prompts().get(1).contains("--- MULTIMODAL INPUT RULES ---")),
                () -> assertTrue(model.prompts().get(1).contains("correct arguments were passed")),
                () -> assertTrue(model.prompts().get(1).contains("required arguments were missing or malformed")),
                () -> assertTrue(model.prompts().get(1).contains("e.g., 0.25, 0.5, 0.75")),
                () -> assertTrue(model.prompts().get(1).contains("Do NOT evaluate tool choice")),
                () -> assertTrue(model.prompts().get(1).contains("Example Output")),
                () -> assertTrue(model.prompts().get(1).contains("weather.search")));
    }

    private static LlmTestCase testCase() {
        return LlmTestCase.builder("weather in Paris")
                .actualOutput("It is sunny.")
                .mcpServers(List.of(Map.of(
                        "server_name", "weather",
                        "available_tools", List.of(Map.of("name", "weather.search", "arguments", List.of("city"))))))
                .mcpToolsCalled(List.of(Map.of(
                        "name", "weather.search",
                        "arguments", Map.of("city", "Paris"))))
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

    private static final class StubMCPUseMetric extends MCPUseMetric {
        private final MCPPrimitivesScore primitivesScore;
        private final MCPArgsScore argsScore;

        StubMCPUseMetric(MCPPrimitivesScore primitivesScore, MCPArgsScore argsScore) {
            this(primitivesScore, argsScore, false);
        }

        StubMCPUseMetric(MCPPrimitivesScore primitivesScore, MCPArgsScore argsScore, boolean strictMode) {
            super(0.5, true, strictMode);
            this.primitivesScore = primitivesScore;
            this.argsScore = argsScore;
        }

        @Override
        protected MCPPrimitivesScore getPrimitivesUsedScore(
                LlmTestCase testCase,
                String availablePrimitives,
                String primitivesUsed) {
            return primitivesScore;
        }

        @Override
        protected MCPArgsScore getArgumentCorrectnessScore(
                LlmTestCase testCase,
                String availablePrimitives,
                String primitivesUsed) {
            return argsScore;
        }
    }
}
