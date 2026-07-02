package dev.jeval;

import java.util.List;

public final class TestCaseUtils {
    private TestCaseUtils() {
    }

    public static void checkValidTestCasesType(List<?> testCases) {
        var llmTestCaseCount = 0;
        var conversationalTestCaseCount = 0;
        for (var testCase : testCases) {
            if (testCase instanceof LlmTestCase) {
                llmTestCaseCount++;
            } else {
                conversationalTestCaseCount++;
            }
        }
        if (llmTestCaseCount > 0 && conversationalTestCaseCount > 0) {
            throw new IllegalArgumentException(
                    "You cannot supply a mixture of `LLMTestCase`(s) and `ConversationalTestCase`(s) as the list of test cases.");
        }
    }
}
