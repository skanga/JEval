package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallTest {

    @Test
    void equalityUsesNameInputParametersAndOutputOnly() {
        var first = new ToolCall("Search", "first description", "first reason", Map.of("q", "refund"), "ok");
        var second = new ToolCall("Search", "second description", "second reason", Map.of("q", "refund"), "ok");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equalityChangesWhenInputParametersChange() {
        var first = new ToolCall("Search", Map.of("q", "refund"), "ok");
        var second = new ToolCall("Search", Map.of("q", "shipping"), "ok");

        assertNotEquals(first, second);
    }

    @Test
    void inputParametersAllowNullValues() {
        var parameters = new java.util.LinkedHashMap<String, Object>();
        parameters.put("present", "value");
        parameters.put("missing", null);

        var toolCall = new ToolCall("Search", parameters, "ok");

        assertEquals(parameters, toolCall.inputParameters());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void inputParametersRejectNonStringKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolCall("Search", (Map) Map.of(123, "refund"), "ok"));
    }

    @Test
    void nameOnlyConstructorMatchesDeepEvalUsage() {
        assertEquals("ImageAnalysis", new ToolCall("ImageAnalysis").name());
    }

    @Test
    void nameIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ToolCall(null));
    }

    @Test
    void stringRepresentationIncludesPopulatedDeepEvalFields() {
        var toolCall = new ToolCall("Search", "Searches policies", "Need policy data", Map.of("q", "refund"), "ok");

        var value = toolCall.toString();

        assertTrue(value.contains("ToolCall("));
        assertTrue(value.contains("name=\"Search\""));
        assertTrue(value.contains("description=\"Searches policies\""));
        assertTrue(value.contains("reasoning=\"Need policy data\""));
        assertTrue(value.contains("input_parameters="));
        assertTrue(value.contains("output='ok'"));
    }

    @Test
    void stringRepresentationOmitsMissingOptionalFields() {
        var value = new ToolCall("Search").toString();

        assertTrue(value.contains("ToolCall("));
        assertTrue(value.contains("name=\"Search\""));
        assertFalse(value.contains("description="));
        assertFalse(value.contains("reasoning="));
        assertFalse(value.contains("input_parameters="));
        assertFalse(value.contains("output="));
    }

    @Test
    void stringRepresentationUsesDeepEvalMultilineShape() {
        assertEquals("""
                ToolCall(
                    name="Search"
                )""", new ToolCall("Search").toString());
    }

    @Test
    void stringRepresentationFormatsInputParametersLikeDeepEvalJson() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("query", "refund");

        assertEquals("""
                ToolCall(
                    name="Search",
                    input_parameters={
                        "query": "refund"
                    }
                )""", new ToolCall("Search", parameters, null).toString());
    }

    @Test
    void stringRepresentationFormatsMapOutputLikeDeepEvalJson() {
        var output = new LinkedHashMap<String, Object>();
        output.put("status", "ok");

        assertEquals("""
                ToolCall(
                    name="Search",
                    output={
                        "status": "ok"
                    }
                )""", new ToolCall("Search", null, output).toString());
    }

    @Test
    void stringRepresentationEscapesStringOutputLikePythonRepr() {
        assertTrue(new ToolCall("Search", null, "line\nbreak")
                .toString()
                .contains("output='line\\nbreak'"));
    }

    @Test
    void stringRepresentationChoosesDoubleQuotesForApostropheOutputLikePythonRepr() {
        assertTrue(new ToolCall("Search", null, "can't")
                .toString()
                .contains("output=\"can't\""));
    }

    @Test
    void stringRepresentationFormatsBooleanOutputLikePythonRepr() {
        assertTrue(new ToolCall("Search", null, true)
                .toString()
                .contains("output=True"));
    }

    @Test
    void stringRepresentationFormatsListOutputLikePythonRepr() {
        assertTrue(new ToolCall("Search", null, java.util.List.of("refund", true, false))
                .toString()
                .contains("output=['refund', True, False]"));
    }

    @Test
    void stringRepresentationFormatsMapInsideListOutputLikePythonRepr() {
        var item = new LinkedHashMap<String, Object>();
        item.put("status", "ok");
        item.put("cached", true);
        item.put("missing", null);

        assertTrue(new ToolCall("Search", null, java.util.List.of(item))
                .toString()
                .contains("output=[{'status': 'ok', 'cached': True, 'missing': None}]"));
    }

    @Test
    void stringRepresentationOmitsEmptyInputParametersLikeDeepEval() {
        var value = new ToolCall("Search", Map.of(), null).toString();

        assertFalse(value.contains("input_parameters="));
    }

    @Test
    void modelDumpByAliasUsesDeepEvalInputParametersAlias() {
        var toolCall = new ToolCall("Search", "Searches", "Need data", Map.of("query", "refund"), "ok");

        var dump = toolCall.modelDump(true);

        assertEquals(Map.of(
                "name", "Search",
                "description", "Searches",
                "reasoning", "Need data",
                "inputParameters", Map.of("query", "refund"),
                "output", "ok"), dump);
        assertFalse(dump.containsKey("input_parameters"));
    }
}
