package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.MultiTurnParam;
import dev.jeval.SingleTurnParam;
import dev.jeval.Turn;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GEvalUtilsTest {

    @Test
    void constructTestCaseStringIncludesSingleTurnMetadataAndTags() {
        var testCase = LlmTestCase.builder("input")
                .metadata(Map.of("source", "unit"))
                .tags(List.of("tag"))
                .build();

        var text = GEvalUtils.constructTestCaseString(
                List.of(SingleTurnParam.METADATA, SingleTurnParam.TAGS),
                testCase);
        var payload = GEvalUtils.constructUploadPayload(
                "metadata-test",
                List.of(SingleTurnParam.METADATA, SingleTurnParam.TAGS),
                "criteria");

        assertTrue(text.contains("Metadata"));
        assertTrue(text.contains("Tags"));
        assertEquals(List.of("metadata", "tags"), payload.get("evaluationParams"));
    }

    @Test
    void constructConversationalFieldsAndPayloadIncludeMetadataAndTags() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .metadata(Map.of("case", "metadata"))
                .tags(List.of("tag"))
                .build();

        var text = GEvalUtils.constructNonTurnsTestCaseString(
                List.of(MultiTurnParam.METADATA, MultiTurnParam.TAGS),
                testCase);
        var payload = GEvalUtils.constructConversationalUploadPayload(
                "conversational-metadata-test",
                List.of(MultiTurnParam.METADATA, MultiTurnParam.TAGS),
                "criteria");

        assertTrue(text.contains("Conversation-level fields"));
        assertTrue(text.contains("Metadata"));
        assertTrue(text.contains("case"));
        assertTrue(text.contains("Tags"));
        assertTrue(text.contains("tag"));
        assertEquals(List.of("metadata", "tags"), payload.get("evaluationParams"));
        assertEquals(true, payload.get("multiTurn"));
    }

    @Test
    void constructUploadPayloadCanUseEvaluationStepsAndRubric() {
        var payload = GEvalUtils.constructUploadPayload(
                "rubric-test",
                List.of(SingleTurnParam.INPUT),
                null,
                List.of("check answer"),
                List.of(new GEvalUtils.Rubric(0, 5, "poor")));

        assertEquals(List.of("input"), payload.get("evaluationParams"));
        assertEquals(List.of("check answer"), payload.get("evaluationSteps"));
        assertEquals(List.of(Map.of("scoreRange", List.of(0, 5), "expectedOutcome", "poor")),
                payload.get("rubric"));
    }

    @Test
    void constructUploadPayloadRejectsEmptyOrUnsupportedParams() {
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.constructUploadPayload("bad", List.of(), "criteria"));
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.constructUploadPayload("bad", List.of(SingleTurnParam.MCP_SERVERS), "criteria"));
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.constructConversationalUploadPayload("bad", List.of(MultiTurnParam.CHATBOT_ROLE), "criteria"));
    }

    @Test
    void validateCriteriaAndEvaluationStepsRejectsMissingBlankOrEmptyInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.validateCriteriaAndEvaluationSteps(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.validateCriteriaAndEvaluationSteps("  ", null));
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.validateCriteriaAndEvaluationSteps(null, List.of()));

        GEvalUtils.validateCriteriaAndEvaluationSteps("criteria", null);
        GEvalUtils.validateCriteriaAndEvaluationSteps(null, List.of("step"));
    }

    @Test
    void ensureRequiredParamsRejectsMissingEvaluationParams() {
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.ensureRequiredParams(List.of(), "criteria", null, "evaluate"));

        GEvalUtils.ensureRequiredParams(List.of(SingleTurnParam.INPUT), "criteria", null, "evaluate");
    }

    @Test
    void validateAndFormatRubricsSortsRejectsOverlapAndFormatsRanges() {
        var sorted = GEvalUtils.validateAndSortRubrics(List.of(
                new GEvalUtils.Rubric(6, 10, "excellent"),
                new GEvalUtils.Rubric(0, 5, "poor")));

        assertEquals(0, sorted.getFirst().start());
        assertEquals("0-5: poor\n6-10: excellent", GEvalUtils.formatRubrics(sorted));
        assertNull(GEvalUtils.validateAndSortRubrics(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new GEvalUtils.Rubric(-1, 5, "bad"));
        assertThrows(IllegalArgumentException.class, () -> new GEvalUtils.Rubric(6, 5, "bad"));
        assertThrows(IllegalArgumentException.class, () -> GEvalUtils.validateAndSortRubrics(List.of(
                new GEvalUtils.Rubric(0, 5, "poor"),
                new GEvalUtils.Rubric(5, 10, "overlap"))));
    }

    @Test
    void constructPullEvaluationParamsMapsApiNames() {
        assertEquals(List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT),
                GEvalUtils.constructPullEvaluationParams(List.of("input", "actualOutput"), false));
        assertEquals(List.of(MultiTurnParam.ROLE, MultiTurnParam.CONTENT),
                GEvalUtils.constructPullEvaluationParams(List.of("role", "content"), true));

        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.constructPullEvaluationParams(List.of(), false));
        assertThrows(IllegalArgumentException.class,
                () -> GEvalUtils.constructPullEvaluationParams(List.of("unsupported"), false));
    }

    @Test
    void constructParamStringsUseOxfordCommaStyle() {
        assertEquals("Input", GEvalUtils.constructParamsString(List.of(SingleTurnParam.INPUT)));
        assertEquals("Input and Actual Output",
                GEvalUtils.constructParamsString(List.of(SingleTurnParam.INPUT, SingleTurnParam.ACTUAL_OUTPUT)));
        assertEquals("Input, Actual Output, and Expected Output", GEvalUtils.constructParamsString(List.of(
                SingleTurnParam.INPUT,
                SingleTurnParam.ACTUAL_OUTPUT,
                SingleTurnParam.EXPECTED_OUTPUT)));

        assertEquals("Role and Content",
                GEvalUtils.constructConversationalTurnParamsString(List.of(MultiTurnParam.ROLE, MultiTurnParam.CONTENT)));
    }

    @Test
    void numberingHelpersAndScoreRangeMatchDeepEval() {
        assertEquals("1. first\n2. second\n", GEvalUtils.numberEvaluationSteps(List.of("first", "second")));
        assertEquals("0. input\n1. output\n", GEvalUtils.numberTestCaseContents(List.of("input", "output")));
        assertEquals(List.of(0, 10), GEvalUtils.getScoreRange(null));
        assertEquals(List.of(2, 8), GEvalUtils.getScoreRange(List.of(
                new GEvalUtils.Rubric(2, 4, "low"),
                new GEvalUtils.Rubric(5, 8, "high"))));
    }

    @Test
    void metricPullResponseKeepsDeepEvalFieldsAndCopiesLists() {
        var steps = new java.util.ArrayList<>(List.of("step"));
        var required = new java.util.ArrayList<>(List.of("input"));
        var rubric = new java.util.ArrayList<>(List.of(
                new GEvalUtils.ApiRubric(List.of(0.0, 5.0), "poor")));

        var response = new GEvalUtils.MetricPullResponse(
                "metric-1",
                "criteria",
                steps,
                required,
                rubric);
        var emptyDefaults = new GEvalUtils.MetricPullResponse(null, null, null, null, null);

        steps.add("ignored");
        required.add("ignored");
        rubric.add(new GEvalUtils.ApiRubric(List.of(6.0, 10.0), "excellent"));

        assertEquals("metric-1", response.id());
        assertEquals("criteria", response.criteria());
        assertEquals(List.of("step"), response.evaluationSteps());
        assertEquals(List.of("input"), response.requiredParameters());
        assertEquals(List.of(0.0, 5.0), response.rubric().getFirst().scoreRange());
        assertEquals("poor", response.rubric().getFirst().expectedOutcome());
        assertEquals(List.of(), emptyDefaults.requiredParameters());
    }
}
