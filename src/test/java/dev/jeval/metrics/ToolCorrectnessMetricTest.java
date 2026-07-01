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
import dev.jeval.ToolCallParam;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCorrectnessMetricTest {

    @Test
    void nonExactModeScoresExpectedToolsCalledInAnyOrder() {
        var testCase = testCase(
                List.of(new ToolCall("Search"), new ToolCall("Lookup")),
                List.of(new ToolCall("Lookup"), new ToolCall("Search")));

        var result = new ToolCorrectnessMetric().measure(testCase);

        assertAll(
                () -> assertEquals("Tool Correctness", result.name()),
                () -> assertEquals(1.0, result.score()),
                () -> assertTrue(result.success()));
    }

    @Test
    void reasonSeparatesToolCallingAndSelectionReasonsLikeDeepEval() {
        var testCase = testCase(
                List.of(new ToolCall("Search")),
                List.of(new ToolCall("Search")));

        var result = new ToolCorrectnessMetric().measure(testCase);

        assertAll(
                () -> assertTrue(result.reason().contains("Tool Calling Reason:")),
                () -> assertTrue(result.reason().contains("Tool Selection Reason:")),
                () -> assertTrue(result.reason().contains("All expected tools [Search] were called (order not considered).")));
    }

    @Test
    void nonExactModeScoresPartialExpectedToolCoverage() {
        var testCase = testCase(
                List.of(new ToolCall("Search")),
                List.of(new ToolCall("Search"), new ToolCall("Lookup")));

        var result = new ToolCorrectnessMetric().measure(testCase);

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.success()),
                () -> assertTrue(result.reason().contains("Incomplete tool usage: missing tools [ToolCall(")),
                () -> assertTrue(result.reason().contains("Lookup")));
    }

    @Test
    void strictModeZerosPartialScoresAndRequiresPerfectMatch() {
        var testCase = testCase(
                List.of(new ToolCall("Search")),
                List.of(new ToolCall("Search"), new ToolCall("Lookup")));

        var result = new ToolCorrectnessMetric(List.of(), false, false, 0.5, true).measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertFalse(result.success()),
                () -> assertEquals(1.0, result.threshold()));
    }

    @Test
    void constructorRejectsNonFiniteThresholds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ToolCorrectnessMetric(List.of(), false, false, Double.NaN, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ToolCorrectnessMetric(List.of(), false, false, Double.POSITIVE_INFINITY, false)));
    }

    @Test
    void exactModeRequiresSameToolsInSameOrder() {
        var testCase = testCase(
                List.of(new ToolCall("Search"), new ToolCall("Lookup")),
                List.of(new ToolCall("Lookup"), new ToolCall("Search")));

        var result = new ToolCorrectnessMetric(true, false).measure(testCase);

        assertAll(
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("Not an exact match: expected [Lookup, Search], called [Search, Lookup].")));
    }

    @Test
    void orderingModeScoresWeightedLongestCommonSubsequence() {
        var testCase = testCase(
                List.of(new ToolCall("Search"), new ToolCall("Lookup")),
                List.of(new ToolCall("Lookup"), new ToolCall("Search")));

        var result = new ToolCorrectnessMetric(false, true).measure(testCase);

        assertAll(
                () -> assertEquals(0.5, result.score()),
                () -> assertTrue(result.reason().contains("out-of-order tools [Search]")));
    }

    @Test
    void orderingModeReasonReportsCorrectOrderingLikeDeepEval() {
        var testCase = testCase(
                List.of(new ToolCall("Search"), new ToolCall("Lookup")),
                List.of(new ToolCall("Search"), new ToolCall("Lookup")));

        var result = new ToolCorrectnessMetric(false, true).measure(testCase);

        assertTrue(result.reason().contains(
                "Correct ordering: all expected tools [Search, Lookup] were called in the correct order."));
    }

    @Test
    void orderingModeReasonReportsMissingToolsLikeDeepEval() {
        var testCase = testCase(
                List.of(new ToolCall("Search")),
                List.of(new ToolCall("Search"), new ToolCall("Lookup")));

        var result = new ToolCorrectnessMetric(false, true).measure(testCase);

        assertTrue(result.reason().contains("missing tools [Lookup]"));
    }

    @Test
    void orderingModeReasonTreatsParameterMismatchAsOutOfOrderLikeDeepEval() {
        var called = new ToolCall("Search", Map.of("query", "refund"), null);
        var expected = new ToolCall("Search", Map.of("query", "shipping"), null);
        var testCase = testCase(List.of(called), List.of(expected));

        var result = new ToolCorrectnessMetric(
                List.of(ToolCallParam.INPUT_PARAMETERS),
                false,
                true,
                0.5).measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertTrue(result.reason().contains("out-of-order tools [Search]")));
    }

    @Test
    void inputParameterEvaluationAffectsScore() {
        var called = new ToolCall("Search", Map.of("query", "refund"), null);
        var expected = new ToolCall("Search", Map.of("query", "shipping"), null);
        var testCase = testCase(List.of(called), List.of(expected));

        var result = new ToolCorrectnessMetric(List.of(ToolCallParam.INPUT_PARAMETERS)).measure(testCase);

        assertAll(
                () -> assertEquals(0.0, result.score()),
                () -> assertTrue(result.reason().contains("missing tools [ToolCall(")),
                () -> assertTrue(result.reason().contains("\"shipping\"")));
    }

    @Test
    void inputParameterEvaluationScoresNestedMapsRecursively() {
        var called = new ToolCall("Search",
                Map.of("filters", Map.of("type", "book", "lang", "fr")),
                null);
        var expected = new ToolCall("Search",
                Map.of("filters", Map.of("type", "book", "lang", "en")),
                null);
        var testCase = testCase(List.of(called), List.of(expected));

        var result = new ToolCorrectnessMetric(List.of(ToolCallParam.INPUT_PARAMETERS)).measure(testCase);

        assertEquals(0.5, result.score());
    }

    @Test
    void exactModeWithInputParametersRequiresExactParameterMatchLikeDeepEval() {
        var called = new ToolCall("Search",
                Map.of("filters", Map.of("type", "book", "lang", "fr")),
                null);
        var expected = new ToolCall("Search",
                Map.of("filters", Map.of("type", "book", "lang", "en")),
                null);
        var testCase = testCase(List.of(called), List.of(expected));

        var result = new ToolCorrectnessMetric(
                List.of(ToolCallParam.INPUT_PARAMETERS),
                true,
                false,
                0.5).measure(testCase);

        assertEquals(0.0, result.score());
    }

    @Test
    void availableToolsSelectionScoreCapsToolCallingScore() {
        var testCase = testCase(
                List.of(new ToolCall("Search")),
                List.of(new ToolCall("Search")));
        EvaluationModel model = prompt -> "{\"score\":0.4,\"reason\":\"Weak available-tool choice.\"}";

        var result = new ToolCorrectnessMetric(List.of(new ToolCall("Search")), model).measure(testCase);

        assertAll(
                () -> assertEquals(0.4, result.score()),
                () -> assertFalse(result.success()),
                () -> assertTrue(result.reason().contains("All expected tools [Search] were called (order not considered).")),
                () -> assertTrue(result.reason().contains("Weak available-tool choice.")));
    }

    @Test
    void measureRequiresToolsCalledAndExpectedTools() {
        var testCase = new LlmTestCase("input", "actual", null);

        assertThrows(MissingTestCaseParamsException.class, () -> new ToolCorrectnessMetric().measure(testCase));
    }

    @Test
    void measureAllowsEmptyInputLikeDeepEval() {
        var testCase = LlmTestCase.builder("")
                .toolsCalled(List.of())
                .expectedTools(List.of())
                .build();

        var result = new ToolCorrectnessMetric().measure(testCase);

        assertEquals(1.0, result.score());
    }

    private static LlmTestCase testCase(List<ToolCall> toolsCalled, List<ToolCall> expectedTools) {
        return LlmTestCase.builder("Use tools")
                .actualOutput("done")
                .toolsCalled(toolsCalled)
                .expectedTools(expectedTools)
                .build();
    }
}
