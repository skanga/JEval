package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestCaseUtilsTest {

    @Test
    void checkValidTestCasesTypeAllowsSingleTestCaseKind() {
        assertDoesNotThrow(() -> TestCaseUtils.checkValidTestCasesType(List.of(
                LlmTestCase.builder("first").build(),
                LlmTestCase.builder("second").build())));
        assertDoesNotThrow(() -> TestCaseUtils.checkValidTestCasesType(List.of(
                ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build())));
    }

    @Test
    void checkValidTestCasesTypeRejectsMixedLlmAndConversationalTestCasesLikeDeepEval() {
        var cases = List.of(
                LlmTestCase.builder("single").build(),
                ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build());

        assertThrows(IllegalArgumentException.class, () -> TestCaseUtils.checkValidTestCasesType(cases));
    }
}
