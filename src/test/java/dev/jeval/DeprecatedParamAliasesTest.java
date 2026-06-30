package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class DeprecatedParamAliasesTest {

    @Test
    void llmTestCaseParamsDelegatesToSingleTurnParamValues() {
        assertSame(SingleTurnParam.INPUT, LLMTestCaseParams.INPUT);
        assertSame(SingleTurnParam.ACTUAL_OUTPUT, LLMTestCaseParams.ACTUAL_OUTPUT);
        assertSame(SingleTurnParam.EXPECTED_OUTPUT, LLMTestCaseParams.EXPECTED_OUTPUT);
        assertSame(SingleTurnParam.MCP_PROMPTS_CALLED, LLMTestCaseParams.MCP_PROMPTS_CALLED);
    }

    @Test
    void turnParamsDelegatesToMultiTurnParamValues() {
        assertSame(MultiTurnParam.ROLE, TurnParams.ROLE);
        assertSame(MultiTurnParam.CONTENT, TurnParams.CONTENT);
        assertSame(MultiTurnParam.EXPECTED_OUTCOME, TurnParams.EXPECTED_OUTCOME);
        assertSame(MultiTurnParam.MCP_PROMPTS, TurnParams.MCP_PROMPTS);
    }
}
