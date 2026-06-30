package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestRunPayloadTest {

    @Test
    void testRunPayloadRecordsExposeDeepEvalDefaultsAndDefensiveCopies() {
        var scoresInput = new ArrayList<>(List.of(0.2, 0.8));
        var scoreType = new MetricScoreType("faithfulness", 0.5);
        var scores = new MetricScores("faithfulness", scoresInput, 1, 1, 0);
        scoresInput.add(1.0);

        var traceScores = new TraceMetricScores();
        var promptMessages = new ArrayList<>(List.of(new PromptMessage("user", "Hello {{name}}")));
        var prompt = new PromptData(
                "prompt",
                "abc123",
                "v1",
                null,
                promptMessages,
                null,
                null,
                PromptInterpolationType.MUSTACHE);
        promptMessages.add(new PromptMessage("assistant", "Hi"));

        var remaining = new RemainingTestRun("run-1");
        var run = new TestRun();

        assertAll(
                () -> assertEquals("all", TestRunResultDisplay.ALL.value()),
                () -> assertEquals("failing", TestRunResultDisplay.FAILING.value()),
                () -> assertEquals("passing", TestRunResultDisplay.PASSING.value()),
                () -> assertEquals("faithfulness", scoreType.metric()),
                () -> assertEquals(0.5, scoreType.score()),
                () -> assertEquals(List.of(0.2, 0.8), scores.scores()),
                () -> assertThrows(UnsupportedOperationException.class, () -> scores.scores().add(0.5)),
                () -> assertTrue(traceScores.agent().isEmpty()),
                () -> assertTrue(traceScores.tool().isEmpty()),
                () -> assertTrue(traceScores.retriever().isEmpty()),
                () -> assertTrue(traceScores.llm().isEmpty()),
                () -> assertTrue(traceScores.base().isEmpty()),
                () -> assertEquals(List.of(new PromptMessage("user", "Hello {{name}}")), prompt.messagesTemplate()),
                () -> assertEquals("run-1", remaining.testRunId()),
                () -> assertEquals(List.of(), remaining.testCases()),
                () -> assertEquals(List.of(), remaining.conversationalTestCases()),
                () -> assertEquals(List.of(), run.testCases()),
                () -> assertEquals(List.of(), run.conversationalTestCases()),
                () -> assertEquals(List.of(), run.metricsScores()),
                () -> assertEquals(0.0, run.runDuration()),
                () -> assertFalse(run.official()));
    }

    @Test
    void testRunAddTestCaseRoutesPayloadTypeAndAccumulatesEvaluationCost() {
        var singleTurn = llmApi(0.25);
        var conversational = conversationalApi(0.4);

        var updated = new TestRun()
                .addTestCase(singleTurn)
                .addTestCase(conversational);

        assertAll(
                () -> assertEquals(List.of(singleTurn), updated.testCases()),
                () -> assertEquals(List.of(conversational), updated.conversationalTestCases()),
                () -> assertEquals(0.65, updated.evaluationCost()));
    }

    @Test
    void traceMetricScoresDefensivelyCopiesNestedScoreMaps() {
        var scores = new MetricScores("faithfulness", List.of(1.0), 1, 0, 0);
        var nested = new java.util.LinkedHashMap<String, MetricScores>();
        nested.put("root", scores);
        var agent = new java.util.LinkedHashMap<String, Map<String, MetricScores>>();
        agent.put("span-1", nested);

        var traceScores = new TraceMetricScores(agent, null, null, null, null);
        nested.put("later", new MetricScores("answer relevancy", List.of(0.0), 0, 1, 0));

        assertAll(
                () -> assertEquals(Map.of("root", scores), traceScores.agent().get("span-1")),
                () -> assertThrows(UnsupportedOperationException.class, () -> traceScores.agent().put("x", Map.of())),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> traceScores.agent().get("span-1").put("x", scores)));
    }

    private static LlmApiTestCase llmApi(Double evaluationCost) {
        return new LlmApiTestCase(
                "case",
                "input",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                List.of(),
                null,
                evaluationCost,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static ConversationalApiTestCase conversationalApi(Double evaluationCost) {
        return new ConversationalApiTestCase(
                "conversation",
                true,
                List.of(),
                0.0,
                evaluationCost,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
