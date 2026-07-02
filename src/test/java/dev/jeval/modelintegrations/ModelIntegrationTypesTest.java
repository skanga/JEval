package dev.jeval.modelintegrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.ToolCall;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModelIntegrationTypesTest {

    @Test
    void inputParametersStoresOptionalIntegrationInputsWithDefensiveCopies() {
        var tools = new ArrayList<Map<String, Object>>();
        tools.add(new LinkedHashMap<>(Map.of("name", "search")));
        var messages = new ArrayList<Map<String, Object>>();
        messages.add(new LinkedHashMap<>(Map.of("role", "user", "content", "hi")));
        var descriptions = new LinkedHashMap<String, String>();
        descriptions.put("search", "Search docs");

        var params = new InputParameters(
                "gpt-4.1",
                "hello",
                tools,
                "be concise",
                messages,
                descriptions);
        tools.getFirst().put("name", "changed");
        messages.clear();
        descriptions.put("search", "changed");

        assertEquals("gpt-4.1", params.model());
        assertEquals("hello", params.input());
        assertEquals(List.of(Map.of("name", "search")), params.tools());
        assertEquals("be concise", params.instructions());
        assertEquals(List.of(Map.of("role", "user", "content", "hi")), params.messages());
        assertEquals(Map.of("search", "Search docs"), params.toolDescriptions());
        assertThrows(UnsupportedOperationException.class, () -> params.tools().add(Map.of()));
    }

    @Test
    void outputParametersStoresModelOutputsAndToolCallsWithDefensiveCopy() {
        var calls = new ArrayList<>(List.of(new ToolCall("search", Map.of("q", "docs"), "found")));

        var params = new OutputParameters("answer", 3, 5, calls);
        calls.clear();

        assertEquals("answer", params.output());
        assertEquals(3, params.promptTokens());
        assertEquals(5, params.completionTokens());
        assertEquals(List.of(new ToolCall("search", Map.of("q", "docs"), "found")), params.toolsCalled());
        assertThrows(UnsupportedOperationException.class, () -> params.toolsCalled().add(new ToolCall("other")));
    }

    @Test
    void outputParametersCalculatesEvaluationCostFromTokenPricesLikeDeepEval() {
        var cost = new OutputParameters("answer", 3, 5, null).evaluationCost(0.01, 0.02);

        assertEquals(0.13, cost.value());
        assertEquals(3, cost.inputTokens());
        assertEquals(5, cost.outputTokens());
    }

    @Test
    void outputParametersKeepsMissingTokenCountsNullWhenCalculatingCostLikeDeepEval() {
        var cost = new OutputParameters("answer", null, 5, null).evaluationCost(0.01, 0.02);

        assertEquals(0.10, cost.value());
        assertNull(cost.inputTokens());
        assertEquals(5, cost.outputTokens());
    }

    @Test
    void outputParametersCostIsUnknownWhenTokenPricesAreIncompleteLikeDeepEval() {
        assertNull(new OutputParameters("answer", 3, 5, null).evaluationCost(null, 0.02));
        assertNull(new OutputParameters("answer", 3, 5, null).evaluationCost(0.01, null));
    }

    @Test
    void recordsAllowMissingValuesLikePydanticModels() {
        var input = new InputParameters();
        var output = new OutputParameters();

        assertNull(input.model());
        assertNull(input.tools());
        assertNull(output.output());
        assertNull(output.toolsCalled());
    }
}
