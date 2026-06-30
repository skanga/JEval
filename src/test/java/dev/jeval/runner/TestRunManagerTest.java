package dev.jeval.runner;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.ApiTestCases;
import dev.jeval.LlmTestCase;
import dev.jeval.MetricData;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunManagerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

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

    @Test
    void saveTestRunWritesDeepEvalAliasJsonAndCreatesParents() throws Exception {
        var manager = new TestRunManager();
        manager.createTestRun("run-id", "EvalTest.java", false);
        var file = tempDir.resolve("nested").resolve("test_run.json");

        manager.saveTestRun(file);
        var saved = JSON.readValue(file.toFile(), Map.class);

        assertAll(
                () -> assertTrue(Files.exists(file)),
                () -> assertEquals("run-id", saved.get("identifier")),
                () -> assertEquals("EvalTest.java", saved.get("testFile")),
                () -> assertTrue(saved.containsKey("testCases")),
                () -> assertFalse(saved.containsKey("test_file")),
                () -> assertFalse(saved.containsKey("evaluationCost")),
                () -> assertFalse(saved.containsKey("datasetAlias")));
    }

    @Test
    void saveTestRunCanWrapPayloadUnderDeepEvalKey() throws Exception {
        var manager = new TestRunManager();
        manager.createTestRun("run-id", "EvalTest.java", false);
        var file = tempDir.resolve("wrapped").resolve("test_run.json");

        manager.saveTestRun(file, "testRun");
        var saved = JSON.readValue(file.toFile(), Map.class);
        var wrapped = (Map<String, Object>) saved.get("testRun");

        assertAll(
                () -> assertEquals(1, saved.size()),
                () -> assertEquals("run-id", wrapped.get("identifier")),
                () -> assertEquals("EvalTest.java", wrapped.get("testFile")),
                () -> assertTrue(wrapped.containsKey("testCases")),
                () -> assertFalse(wrapped.containsKey("test_file")),
                () -> assertFalse(wrapped.containsKey("evaluationCost")));
    }

    @Test
    void saveAndReadFinalTestRunLinkUseDeepEvalLatestRunKeys() throws Exception {
        var manager = new TestRunManager();
        var file = tempDir.resolve(".deepeval").resolve(".latest_test_run.json");

        manager.saveFinalTestRunLink(file, "https://app.confident-ai.com/test-runs/run-id");
        var saved = JSON.readValue(file.toFile(), Map.class);

        assertAll(
                () -> assertTrue(Files.exists(file)),
                () -> assertEquals(Map.of(
                        TestRunManager.LATEST_TEST_RUN_LINK_KEY,
                        "https://app.confident-ai.com/test-runs/run-id"), saved),
                () -> assertEquals(
                        "https://app.confident-ai.com/test-runs/run-id",
                        manager.getLatestTestRunLink(file)));
    }

    @Test
    void getLatestTestRunLinkReturnsNullWhenFileOrKeyIsMissing() throws Exception {
        var manager = new TestRunManager();
        var missing = tempDir.resolve(".deepeval").resolve(".latest_test_run.json");
        var dataOnly = tempDir.resolve("latest_with_data.json");

        Files.writeString(dataOnly, "{\"" + TestRunManager.LATEST_TEST_RUN_DATA_KEY + "\":{}}");

        assertAll(
                () -> assertNull(manager.getLatestTestRunLink(missing)),
                () -> assertNull(manager.getLatestTestRunLink(dataOnly)));
    }

    @Test
    void getLatestTestRunDataReadsWrappedDeepEvalPayload() throws Exception {
        var manager = new TestRunManager();
        var file = tempDir.resolve(".deepeval").resolve(".latest_test_run.json");

        manager.createTestRun("run-id", "EvalTest.java", false);
        manager.saveTestRun(file, TestRunManager.LATEST_TEST_RUN_DATA_KEY);
        var loaded = manager.getLatestTestRunData(file);

        assertAll(
                () -> assertEquals("run-id", loaded.identifier()),
                () -> assertEquals("EvalTest.java", loaded.testFile()),
                () -> assertEquals(List.of(), loaded.testCases()),
                () -> assertEquals(0.0, loaded.runDuration()));
    }

    @Test
    void getLatestTestRunDataReturnsNullWhenFileOrKeyIsMissing() throws Exception {
        var manager = new TestRunManager();
        var missing = tempDir.resolve(".deepeval").resolve(".latest_test_run.json");
        var linkOnly = tempDir.resolve("latest_with_link.json");

        Files.writeString(linkOnly, "{\"" + TestRunManager.LATEST_TEST_RUN_LINK_KEY + "\":\"https://example.test\"}");

        assertAll(
                () -> assertNull(manager.getLatestTestRunData(missing)),
                () -> assertNull(manager.getLatestTestRunData(linkOnly)));
    }
}
