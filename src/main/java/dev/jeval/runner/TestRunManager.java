package dev.jeval.runner;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmApiTestCase;
import dev.jeval.LlmTestCase;

public final class TestRunManager {
    private TestRun testRun;
    private boolean disableRequest;

    public void reset() {
        testRun = null;
        disableRequest = false;
    }

    public void setTestRun(TestRun testRun) {
        this.testRun = testRun;
    }

    public void createTestRun() {
        createTestRun(null, null, false);
    }

    public void createTestRun(String identifier) {
        createTestRun(identifier, null, false);
    }

    public void createTestRun(String identifier, String fileName, boolean disableRequest) {
        this.disableRequest = disableRequest;
        testRun = new TestRun(
                fileName,
                null,
                null,
                null,
                null,
                identifier,
                null,
                null,
                null,
                null,
                0.0,
                null,
                null,
                null,
                false);
    }

    public TestRun getTestRun() {
        return getTestRun(null);
    }

    public TestRun getTestRun(String identifier) {
        if (testRun == null) {
            createTestRun(identifier);
        }
        return testRun;
    }

    public boolean disableRequest() {
        return disableRequest;
    }

    public void updateTestRun(LlmApiTestCase apiTestCase, LlmTestCase testCase) {
        if (shouldSkip(apiTestCase)) {
            return;
        }
        if (testRun == null) {
            createTestRun();
        }
        testRun = testRun.addTestCase(apiTestCase).setDatasetProperties(testCase);
    }

    public void updateTestRun(ConversationalApiTestCase apiTestCase, ConversationalTestCase testCase) {
        if (apiTestCase.metricsData() != null && apiTestCase.metricsData().isEmpty()) {
            return;
        }
        if (testRun == null) {
            createTestRun();
        }
        testRun = testRun.addTestCase(apiTestCase).setDatasetProperties(testCase);
    }

    public void clearTestRun() {
        testRun = null;
    }

    private static boolean shouldSkip(LlmApiTestCase apiTestCase) {
        return apiTestCase.metricsData() != null && apiTestCase.metricsData().isEmpty() && apiTestCase.trace() == null;
    }
}
