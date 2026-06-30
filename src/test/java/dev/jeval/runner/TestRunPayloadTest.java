package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ConversationalApiTestCase;
import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricData;
import dev.jeval.Turn;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunPayloadTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

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
    void testRunModelDumpUsesDeepEvalAliasesAndCanExcludeNulls() {
        var testCase = llmApiWithActualOutput("case", "actual");
        var traceScores = new TraceMetricScores(
                Map.of("planner", Map.of("faithfulness", new MetricScores("faithfulness", List.of(0.8), 1, 0, 0))),
                null,
                null,
                null,
                null);
        var run = new TestRun(
                "EvalTest.java",
                List.of(testCase),
                List.of(),
                List.of(new MetricScores("faithfulness", List.of(0.8), 1, 0, 0)),
                traceScores,
                "run-id",
                Map.of("model", "gpt"),
                null,
                1,
                0,
                2.5,
                null,
                "dataset",
                "dataset-id",
                true);

        var dump = run.modelDump(true, true);
        var nestedCase = ((List<Map<String, Object>>) dump.get("testCases")).getFirst();
        var nestedTraceScores = (Map<String, Object>) dump.get("traceMetricsScores");

        assertAll(
                () -> assertEquals("EvalTest.java", dump.get("testFile")),
                () -> assertEquals("run-id", dump.get("identifier")),
                () -> assertEquals(1, dump.get("testPassed")),
                () -> assertEquals(0, dump.get("testFailed")),
                () -> assertEquals(2.5, dump.get("runDuration")),
                () -> assertEquals("dataset", dump.get("datasetAlias")),
                () -> assertEquals("dataset-id", dump.get("datasetId")),
                () -> assertEquals(true, dump.get("official")),
                () -> assertTrue(dump.containsKey("testCases")),
                () -> assertTrue(dump.containsKey("conversationalTestCases")),
                () -> assertTrue(dump.containsKey("metricsScores")),
                () -> assertTrue(dump.containsKey("traceMetricsScores")),
                () -> assertFalse(dump.containsKey("evaluationCost")),
                () -> assertFalse(dump.containsKey("prompts")),
                () -> assertEquals("actual", nestedCase.get("actualOutput")),
                () -> assertFalse(nestedCase.containsKey("actual_output")),
                () -> assertTrue(nestedTraceScores.containsKey("agent")));
    }

    @Test
    void testRunSaveAndLoadUseDeepEvalAliasJson() throws Exception {
        var run = new TestRun(
                "EvalTest.java",
                List.of(llmApiWithActualOutput("case", "actual")),
                List.of(),
                List.of(new MetricScores("faithfulness", List.of(0.8), 1, 0, 0)),
                null,
                "run-id",
                Map.of("model", "gpt"),
                null,
                1,
                0,
                2.5,
                null,
                "dataset",
                "dataset-id",
                true);
        var file = tempDir.resolve("test_run.json");

        var returned = run.save(file);
        var saved = JSON.readValue(file.toFile(), Map.class);
        var nestedCase = ((List<Map<String, Object>>) saved.get("testCases")).getFirst();
        var loaded = TestRun.load(file);

        assertAll(
                () -> assertEquals(run, returned),
                () -> assertEquals("EvalTest.java", saved.get("testFile")),
                () -> assertTrue(saved.containsKey("testCases")),
                () -> assertFalse(saved.containsKey("test_file")),
                () -> assertFalse(saved.containsKey("evaluationCost")),
                () -> assertFalse(saved.containsKey("prompts")),
                () -> assertEquals("actual", nestedCase.get("actualOutput")),
                () -> assertEquals("run-id", loaded.identifier()),
                () -> assertEquals("EvalTest.java", loaded.testFile()),
                () -> assertEquals(1, loaded.testCases().size()),
                () -> assertEquals("actual", loaded.testCases().getFirst().actualOutput()),
                () -> assertEquals("faithfulness", loaded.metricsScores().getFirst().metric()),
                () -> assertEquals("dataset", loaded.datasetAlias()),
                () -> assertEquals("dataset-id", loaded.datasetId()),
                () -> assertTrue(loaded.official()));
    }

    @Test
    void constructMetricsScoresAggregatesMetricDataAcrossTestCaseTypes() {
        var singleTurn = llmApi(List.of(
                metricData("faithfulness", 0.9, true),
                metricData("faithfulness", 0.2, false),
                metricData("answer relevancy", null, true)));
        var conversational = conversationalApi(List.of(
                metricData("faithfulness", 0.8, true),
                metricData("conversation completeness", 1.0, true)));

        var aggregation = new TestRun()
                .addTestCase(singleTurn)
                .addTestCase(conversational)
                .constructMetricsScores();

        assertAll(
                () -> assertEquals(4, aggregation.validScores()),
                () -> assertEquals(List.of(
                                new MetricScores("faithfulness", List.of(0.9, 0.2, 0.8), 2, 1, 0),
                                new MetricScores("answer relevancy", List.of(), 0, 0, 1),
                                new MetricScores("conversation completeness", List.of(1.0), 1, 0, 0)),
                        aggregation.testRun().metricsScores()),
                () -> assertEquals(List.of(), new TestRun().metricsScores()));
    }

    @Test
    void constructMetricsScoresAggregatesTraceSpanMetricsLikeDeepEval() {
        var agentMetric = metricData("trace faithfulness", 0.7, true);
        var toolMetric = metricData("trace faithfulness", 0.1, false);
        var errorMetric = metricData("trace answer relevancy", null, true);
        var testCase = llmApiWithTrace(Map.of(
                "agentSpans", List.of(Map.of("name", "planner", "metricsData", List.of(agentMetric, errorMetric))),
                "toolSpans", List.of(Map.of("name", "search", "metricsData", List.of(toolMetric)))));

        var aggregation = new TestRun()
                .addTestCase(testCase)
                .constructMetricsScores();

        assertAll(
                () -> assertEquals(2, aggregation.validScores()),
                () -> assertEquals(List.of(
                                new MetricScores("trace faithfulness", List.of(0.7, 0.1), 1, 1, 0),
                                new MetricScores("trace answer relevancy", List.of(), 0, 0, 1)),
                        aggregation.testRun().metricsScores()),
                () -> assertEquals(
                        Map.of("trace faithfulness", new MetricScores("trace faithfulness", List.of(0.7), 1, 0, 0),
                                "trace answer relevancy", new MetricScores("trace answer relevancy", List.of(), 0, 0, 1)),
                        aggregation.testRun().traceMetricsScores().agent().get("planner")),
                () -> assertEquals(
                        Map.of("trace faithfulness", new MetricScores("trace faithfulness", List.of(0.1), 0, 1, 0)),
                        aggregation.testRun().traceMetricsScores().tool().get("search")),
                () -> assertTrue(aggregation.testRun().traceMetricsScores().llm().isEmpty()));
    }

    @Test
    void sortTestCasesMatchesDeepEvalOrderGapAndDuplicateRules() {
        var explicitSingleTurn = llmApi("ordered", 2);
        var duplicateSingleTurnA = llmApi("duplicate-a", 0);
        var duplicateSingleTurnB = llmApi("duplicate-b", 0);
        var missingSingleTurn = llmApi("missing", null);
        var missingConversation = conversationalApi("missing-conversation", null);
        var explicitConversation = conversationalApi("ordered-conversation", 1);

        var sorted = new TestRun()
                .addTestCase(explicitSingleTurn)
                .addTestCase(duplicateSingleTurnA)
                .addTestCase(missingSingleTurn)
                .addTestCase(duplicateSingleTurnB)
                .addTestCase(missingConversation)
                .addTestCase(explicitConversation)
                .sortTestCases();

        assertAll(
                () -> assertEquals(
                        List.of("duplicate-a", "duplicate-b", "ordered", "missing"),
                        sorted.testCases().stream().map(LlmApiTestCase::name).toList()),
                () -> assertEquals(
                        List.of(0, 1, 2, 3),
                        sorted.testCases().stream().map(LlmApiTestCase::order).toList()),
                () -> assertEquals(
                        List.of("ordered-conversation", "missing-conversation"),
                        sorted.conversationalTestCases().stream().map(ConversationalApiTestCase::name).toList()),
                () -> assertEquals(
                        List.of(1, 2),
                        sorted.conversationalTestCases().stream().map(ConversationalApiTestCase::order).toList()),
                () -> assertEquals(2, explicitSingleTurn.order()),
                () -> assertEquals(0, duplicateSingleTurnA.order()),
                () -> assertEquals(0, duplicateSingleTurnB.order()),
                () -> assertEquals(1, explicitConversation.order()));
    }

    @Test
    void setDatasetPropertiesCopiesFirstUnsetDatasetAliasAndId() {
        var singleTurn = LlmTestCase.builder("input")
                .datasetAlias("single-alias")
                .datasetId("single-id")
                .build();
        var conversational = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .datasetAlias("conversation-alias")
                .datasetId("conversation-id")
                .build();
        var preset = new TestRun(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0.0,
                null,
                "existing-alias",
                "existing-id",
                false);

        var fromSingleTurn = new TestRun().setDatasetProperties(singleTurn);
        var fromConversation = new TestRun().setDatasetProperties(conversational);
        var unchanged = preset.setDatasetProperties(singleTurn);

        assertAll(
                () -> assertEquals("single-alias", fromSingleTurn.datasetAlias()),
                () -> assertEquals("single-id", fromSingleTurn.datasetId()),
                () -> assertEquals("conversation-alias", fromConversation.datasetAlias()),
                () -> assertEquals("conversation-id", fromConversation.datasetId()),
                () -> assertEquals("existing-alias", unchanged.datasetAlias()),
                () -> assertEquals("existing-id", unchanged.datasetId()),
                () -> assertEquals(0.0, unchanged.runDuration()),
                () -> assertEquals(List.of(), unchanged.testCases()));
    }

    @Test
    void calculateTestPassesAndFailsCountsOnlyNonNullSuccessValues() {
        var counted = new TestRun()
                .addTestCase(llmApiWithSuccess("single-pass", true))
                .addTestCase(llmApiWithSuccess("single-fail", false))
                .addTestCase(llmApiWithSuccess("single-message", null))
                .addTestCase(conversationalApiWithSuccess("conversation-pass", true))
                .addTestCase(conversationalApiWithSuccess("conversation-fail", false))
                .addTestCase(conversationalApiWithSuccess("conversation-message", null))
                .calculateTestPassesAndFails();

        assertAll(
                () -> assertEquals(2, counted.testPassed()),
                () -> assertEquals(2, counted.testFailed()),
                () -> assertEquals(List.of("single-pass", "single-fail", "single-message"),
                        counted.testCases().stream().map(LlmApiTestCase::name).toList()),
                () -> assertEquals(List.of("conversation-pass", "conversation-fail", "conversation-message"),
                        counted.conversationalTestCases().stream().map(ConversationalApiTestCase::name).toList()),
                () -> assertEquals(0.0, counted.runDuration()),
                () -> assertEquals(List.of(), new TestRun().testCases()));
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
        return llmApi(evaluationCost, List.of());
    }

    private static LlmApiTestCase llmApi(List<Object> metricsData) {
        return llmApi(null, metricsData);
    }

    private static LlmApiTestCase llmApi(Double evaluationCost, List<Object> metricsData) {
        return llmApi("case", null, evaluationCost, metricsData);
    }

    private static LlmApiTestCase llmApi(String name, Integer order) {
        return llmApi(name, order, null, List.of());
    }

    private static LlmApiTestCase llmApiWithSuccess(String name, Boolean success) {
        return new LlmApiTestCase(
                name,
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
                success,
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
                null,
                null);
    }

    private static LlmApiTestCase llmApiWithActualOutput(String name, String actualOutput) {
        return new LlmApiTestCase(
                name,
                "input",
                actualOutput,
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

    private static LlmApiTestCase llmApi(
            String name, Integer order, Double evaluationCost, List<Object> metricsData) {
        return new LlmApiTestCase(
                name,
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
                metricsData,
                null,
                evaluationCost,
                order,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static LlmApiTestCase llmApiWithTrace(Map<String, Object> trace) {
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
                null,
                null,
                null,
                null,
                null,
                trace,
                null,
                null,
                null,
                null);
    }

    private static ConversationalApiTestCase conversationalApi(Double evaluationCost) {
        return conversationalApi(evaluationCost, List.of());
    }

    private static ConversationalApiTestCase conversationalApi(List<Object> metricsData) {
        return conversationalApi(null, metricsData);
    }

    private static ConversationalApiTestCase conversationalApi(Double evaluationCost, List<Object> metricsData) {
        return conversationalApi("conversation", null, evaluationCost, metricsData);
    }

    private static ConversationalApiTestCase conversationalApi(String name, Integer order) {
        return conversationalApi(name, order, null, List.of());
    }

    private static ConversationalApiTestCase conversationalApiWithSuccess(String name, Boolean success) {
        return new ConversationalApiTestCase(
                name,
                success,
                List.of(),
                0.0,
                null,
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

    private static ConversationalApiTestCase conversationalApi(
            String name, Integer order, Double evaluationCost, List<Object> metricsData) {
        return new ConversationalApiTestCase(
                name,
                true,
                metricsData,
                0.0,
                evaluationCost,
                List.of(),
                order,
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

    private static MetricData metricData(String name, Double score, boolean success) {
        return new MetricData(name, 0.5, success, score, null, false, null, null, null, null, null, null);
    }
}
