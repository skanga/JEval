package dev.jeval.runner;

import dev.jeval.ConversationalApiTestCase;
import dev.jeval.LlmApiTestCase;
import java.util.List;

public record RemainingTestRun(
        String testRunId,
        List<LlmApiTestCase> testCases,
        List<ConversationalApiTestCase> conversationalTestCases) {

    public RemainingTestRun(String testRunId) {
        this(testRunId, null, null);
    }

    public RemainingTestRun {
        testCases = testCases == null ? List.of() : List.copyOf(testCases);
        conversationalTestCases = conversationalTestCases == null ? List.of() : List.copyOf(conversationalTestCases);
    }
}
