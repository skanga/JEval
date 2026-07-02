package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.ArenaTestCase;
import dev.jeval.ConversationalTestCase;
import dev.jeval.Contestant;
import dev.jeval.LlmTestCase;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.MllmImage;
import dev.jeval.MultiTurnParam;
import dev.jeval.SingleTurnParam;
import dev.jeval.Turn;
import dev.jeval.ToolCall;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricUtilsTest {

    @Test
    void checkLlmTestCaseParamsAcceptsMetadataAndTagsWhenPresent() {
        var testCase = LlmTestCase.builder("input")
                .metadata(Map.of("source", "unit"))
                .tags(List.of("tag"))
                .build();

        assertDoesNotThrow(() -> MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.METADATA, SingleTurnParam.TAGS),
                "DummyMetric"));
    }

    @Test
    void checkLlmTestCaseParamsRequiresMetadataWhenSelected() {
        var testCase = LlmTestCase.builder("input").tags(List.of("tag")).build();

        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.METADATA),
                "DummyMetric"));
    }

    @Test
    void checkLlmTestCaseParamsRequiresSelectedNullFieldsAndEmptyActualOutput() {
        var missingFields = LlmTestCase.builder("input").actualOutput("answer").build();
        var emptyActualOutput = LlmTestCase.builder("input").actualOutput("").build();

        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkLlmTestCaseParams(
                missingFields,
                List.of(SingleTurnParam.EXPECTED_OUTPUT, SingleTurnParam.CONTEXT),
                "DummyMetric"));
        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkLlmTestCaseParams(
                emptyActualOutput,
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "DummyMetric"));
    }

    @Test
    void checkLlmTestCaseParamsValidatesInputAndActualOutputImageCountsLikeDeepEval() {
        var image = new MllmImage("aW1hZ2U=", "image/png");
        var testCase = LlmTestCase.builder("input " + image)
                .actualOutput("output " + image)
                .build();

        assertDoesNotThrow(() -> MetricUtils.checkLlmTestCaseParams(
                testCase,
                List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                "ImageMetric",
                1,
                1));
        assertEquals(
                "Can only evaluate test cases with '2' input images using the 'ImageMetric' metric. `1` found.",
                assertThrows(IllegalArgumentException.class, () -> MetricUtils.checkLlmTestCaseParams(
                        testCase,
                        List.of(SingleTurnParam.INPUT),
                        "ImageMetric",
                        2,
                        null)).getMessage());
        assertEquals(
                "Can only evaluate test cases with '2' output images using the 'ImageMetric' metric. `1` found.",
                assertThrows(IllegalArgumentException.class, () -> MetricUtils.checkLlmTestCaseParams(
                        testCase,
                        List.of(SingleTurnParam.ACTUAL_OUTPUT),
                        "ImageMetric",
                        null,
                        2)).getMessage());
    }

    @Test
    void checkArenaTestCaseParamsValidatesEachContestantTestCase() {
        var arena = new ArenaTestCase(List.of(
                new Contestant("first", LlmTestCase.builder("input")
                        .actualOutput("first")
                        .expectedOutput("expected")
                        .build()),
                new Contestant("second", LlmTestCase.builder("input")
                        .actualOutput("second")
                        .expectedOutput("expected")
                        .build())));
        var missingActualOutput = new ArenaTestCase(List.of(
                new Contestant("first", LlmTestCase.builder("input")
                        .actualOutput("first")
                        .expectedOutput("expected")
                        .build()),
                new Contestant("second", LlmTestCase.builder("input")
                        .expectedOutput("expected")
                        .build())));

        assertDoesNotThrow(() -> MetricUtils.checkArenaTestCaseParams(
                arena,
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "DummyArenaMetric"));
        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkArenaTestCaseParams(
                missingActualOutput,
                List.of(SingleTurnParam.ACTUAL_OUTPUT),
                "DummyArenaMetric"));
    }

    @Test
    void checkConversationalTestCaseParamsAcceptsMetadataAndTagsWhenPresent() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .metadata(Map.of("case", "metadata"))
                .tags(List.of("tag"))
                .build();

        assertDoesNotThrow(() -> MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(MultiTurnParam.METADATA, MultiTurnParam.TAGS),
                "DummyConversationalMetric"));
    }

    @Test
    void checkConversationalTestCaseParamsRequiresTagsWhenSelected() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .metadata(Map.of("case", "metadata"))
                .build();

        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(MultiTurnParam.TAGS),
                "DummyConversationalMetric"));
    }

    @Test
    void checkConversationalTestCaseParamsRequiresSelectedScenarioExpectedOutcomeAndChatbotRole() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();

        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(MultiTurnParam.SCENARIO),
                "DummyConversationalMetric"));
        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(MultiTurnParam.EXPECTED_OUTCOME),
                "DummyConversationalMetric"));
        assertThrows(MissingTestCaseParamsException.class, () -> MetricUtils.checkConversationalTestCaseParams(
                testCase,
                List.of(),
                "DummyConversationalMetric",
                true));
    }

    @Test
    void convertTurnToDictIncludesSelectedTurnFieldsAndSkipsCaseLevelParams() {
        var turn = Turn.builder("assistant", "hello")
                .retrievalContext(List.of("context"))
                .metadata(Map.of("turn", "metadata"))
                .build();

        var dict = MetricUtils.convertTurnToDict(
                turn,
                List.of(
                        MultiTurnParam.CONTENT,
                        MultiTurnParam.ROLE,
                        MultiTurnParam.RETRIEVAL_CONTEXT,
                        MultiTurnParam.METADATA,
                        MultiTurnParam.TAGS,
                        MultiTurnParam.SCENARIO,
                        MultiTurnParam.EXPECTED_OUTCOME));

        assertEquals("hello", dict.get("content"));
        assertEquals("assistant", dict.get("role"));
        assertEquals(List.of("context"), dict.get("retrieval_context"));
        assertFalse(dict.containsKey("metadata"));
        assertFalse(dict.containsKey("tags"));
        assertFalse(dict.containsKey("scenario"));
        assertFalse(dict.containsKey("expected_outcome"));
    }

    @Test
    void convertTurnToDictDefaultsToContentAndRole() {
        var dict = MetricUtils.convertTurnToDict(new Turn("user", "hello"));

        assertEquals(Map.of("content", "hello", "role", "user"), dict);
    }

    @Test
    void getTurnsInSlidingWindowReturnsTrailingWindows() {
        var turns = List.of(
                new Turn("user", "one"),
                new Turn("assistant", "two"),
                new Turn("user", "three"));

        assertEquals(List.of(
                List.of(turns.get(0)),
                List.of(turns.get(0), turns.get(1)),
                List.of(turns.get(1), turns.get(2))),
                MetricUtils.getTurnsInSlidingWindow(turns, 2));
    }

    @Test
    void getUnitInteractionsSplitsAfterAssistantThenUserAndDropsIncompleteFinalUnit() {
        var firstUser = new Turn("user", "one");
        var firstAssistant = new Turn("assistant", "two");
        var secondUser = new Turn("user", "three");
        var secondAssistant = new Turn("assistant", "four");
        var danglingUser = new Turn("user", "five");

        assertEquals(List.of(
                List.of(firstUser, firstAssistant),
                List.of(secondUser, secondAssistant)),
                MetricUtils.getUnitInteractions(List.of(firstUser, firstAssistant, secondUser, secondAssistant, danglingUser)));
    }

    @Test
    void formatTurnsIncludesSelectedNonEmptySingleTurnFields() {
        var first = LlmTestCase.builder("question")
                .actualOutput("answer")
                .context(List.of("context"))
                .metadata(Map.of("source", "unit"))
                .tags(List.of("tag"))
                .build();
        var second = LlmTestCase.builder("empty")
                .context(List.of())
                .tags(List.of())
                .build();

        var turns = MetricUtils.formatTurns(
                List.of(first, second),
                List.of(
                        SingleTurnParam.INPUT,
                        SingleTurnParam.ACTUAL_OUTPUT,
                        SingleTurnParam.CONTEXT,
                        SingleTurnParam.METADATA,
                        SingleTurnParam.TAGS));

        assertEquals("question", turns.getFirst().get("input"));
        assertEquals("answer", turns.getFirst().get("actual_output"));
        assertEquals(List.of("context"), turns.getFirst().get("context"));
        assertEquals(Map.of("source", "unit"), turns.getFirst().get("metadata"));
        assertEquals(List.of("tag"), turns.getFirst().get("tags"));
        assertEquals(Map.of("input", "empty"), turns.get(1));
    }

    @Test
    void printToolsCalledFormatsIndentedJsonListLikeDeepEval() {
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("query", "refund");

        assertEquals("""
                [
                  {
                      "name": "Search",
                      "description": null,
                      "reasoning": null,
                      "input_parameters": {
                          "query": "refund"
                      },
                      "output": "ok"
                  }
                ]""", MetricUtils.printToolsCalled(List.of(new ToolCall("Search", parameters, "ok"))));
    }

    @Test
    void printToolsCalledReturnsEmptyStringForNoToolsLikeDeepEval() {
        assertEquals("", MetricUtils.printToolsCalled(List.of()));
    }

    @Test
    void constructVerboseLogsJoinsAllButFinalStepLikeDeepEval() {
        assertEquals(
                "first \n \nsecond",
                MetricUtils.constructVerboseLogs("Faithfulness", List.of("first", "second", "score"), false));
    }

    @Test
    void constructVerboseLogsPrintsMetricHeaderAndFinalStepWhenVerbose() {
        var output = new ByteArrayOutputStream();

        var logs = MetricUtils.constructVerboseLogs(
                "Faithfulness",
                List.of("first", "second", "score"),
                true,
                new PrintStream(output, true, StandardCharsets.UTF_8));

        assertEquals("first \n \nsecond", logs);
        assertEquals("""
                **************************************************
                Faithfulness Verbose Logs
                **************************************************

                first\s
                \s
                second
                \s
                score

                ======================================================================
                """, output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void trimAndLoadJsonExtractsAndRepairsEvaluationJson() {
        var embedded = MetricUtils.trimAndLoadJson("prefix {\"score\": 1, \"reason\": \"ok\"} suffix");
        var missingBrace = MetricUtils.trimAndLoadJson("{\"score\": 1");
        var trailingComma = MetricUtils.trimAndLoadJson("{\"items\": [1, 2,], \"ok\": true,}");

        assertEquals(1, embedded.get("score").asInt());
        assertEquals("ok", embedded.get("reason").asText());
        assertEquals(1, missingBrace.get("score").asInt());
        assertEquals(2, trailingComma.get("items").size());
        assertEquals(true, trailingComma.get("ok").asBoolean());
    }

    @Test
    void trimAndLoadJsonPreservesValidStringsThatLookLikeTrailingCommas() {
        var bracket = MetricUtils.trimAndLoadJson("{\"reason\": \"found items A, B, ] then stopped\"}");
        var brace = MetricUtils.trimAndLoadJson("{\"note\": \"the set is {x, y, } here\"}");

        assertEquals("found items A, B, ] then stopped", bracket.get("reason").asText());
        assertEquals("the set is {x, y, } here", brace.get("note").asText());
    }

    @Test
    void trimAndLoadJsonRejectsMissingOrInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> MetricUtils.trimAndLoadJson(null));
        assertThrows(IllegalArgumentException.class, () -> MetricUtils.trimAndLoadJson("no json here"));
    }

    @Test
    void numericSchemaHelpersRejectNonFiniteValuesLikeDeepEval() {
        var score = MetricUtils.trimAndLoadJson("{\"score\":\"NaN\"}");
        var scores = MetricUtils.trimAndLoadJson("{\"scores\":[\"Infinity\"]}");

        assertThrows(IllegalArgumentException.class, () -> MetricUtils.requiredDouble(score, "score"));
        assertThrows(IllegalArgumentException.class, () -> MetricUtils.requiredDoubleList(scores, "scores"));
    }
}
