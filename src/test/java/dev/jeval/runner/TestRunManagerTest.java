package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.jeval.ApiTestCases;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestRunManagerTest {

    @Test
    void createAndGetTestRunUseDeepEvalDefaults() {
        var manager = new TestRunManager();

        manager.createTestRun("run-id", "EvalTest.java", true);
        var run = manager.getTestRun();

        assertAll(
                () -> assertEquals("run-id", run.identifier()),
                () -> assertEquals("EvalTest.java", run.testFile()),
                () -> assertEquals(List.of(), run.testCases()),
                () -> assertEquals(List.of(), run.conversationalTestCases()),
                () -> assertEquals(List.of(), run.metricsScores()),
                () -> assertNull(run.hyperparameters()),
                () -> assertNull(run.testPassed()),
                () -> assertNull(run.testFailed()),
                () -> assertEquals(0.0, run.runDuration()),
                () -> assertNull(run.evaluationCost()),
                () -> assertSame(run, manager.getTestRun()));
    }

    @Test
    void getTestRunCreatesRunLazilyAndClearRemovesIt() {
        var manager = new TestRunManager();
        var first = manager.getTestRun("lazy-id");

        manager.clearTestRun();
        var second = manager.getTestRun();

        assertAll(
                () -> assertEquals("lazy-id", first.identifier()),
                () -> assertNull(second.identifier()),
                () -> assertEquals(List.of(), second.testCases()));
    }

    @Test
    void updateTestRunSkipsEmptyMetricCasesWithoutTrace() {
        var manager = new TestRunManager();
        var source = LlmTestCase.builder("input")
                .datasetAlias("dataset")
                .datasetId("dataset-id")
                .build();
        var api = ApiTestCases.from(source, 0);

        manager.updateTestRun(api, source);
        var run = manager.getTestRun();

        assertAll(
                () -> assertEquals(List.of(), run.testCases()),
                () -> assertNull(run.datasetAlias()),
                () -> assertNull(run.datasetId()));
    }

    @Test
    void updateTestRunAddsApiCaseAndDatasetPropertiesWhenMetricsOrTraceExist() {
        var manager = new TestRunManager();
        var metric = new MetricData("faithfulness", 0.8, true, 0.5, "ok", false, "gpt", null, 0.5, null, null, null);
        var source = LlmTestCase.builder("input")
                .datasetAlias("dataset")
                .datasetId("dataset-id")
                .build();
        var api = ApiTestCases.from(source, 4).updateMetricData(metric);
        var traceOnly = ApiTestCases.from(LlmTestCase.builder("trace").trace(Map.of("agentSpans", List.of())).build(), 5);

        manager.updateTestRun(api, source);
        manager.updateTestRun(traceOnly, source);
        var run = manager.getTestRun();

        assertAll(
                () -> assertEquals(List.of(api, traceOnly), run.testCases()),
                () -> assertEquals("dataset", run.datasetAlias()),
                () -> assertEquals("dataset-id", run.datasetId()),
                () -> assertEquals(0.5, run.evaluationCost()));
    }
}
