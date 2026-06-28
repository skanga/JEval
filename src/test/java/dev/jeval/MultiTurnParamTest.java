package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MultiTurnParamTest {

    @Test
    void valuesMatchDeepEvalMultiTurnParams() {
        assertEquals("role", MultiTurnParam.ROLE.value());
        assertEquals("content", MultiTurnParam.CONTENT.value());
        assertEquals("metadata", MultiTurnParam.METADATA.value());
        assertEquals("tags", MultiTurnParam.TAGS.value());
        assertEquals("scenario", MultiTurnParam.SCENARIO.value());
        assertEquals("expected_outcome", MultiTurnParam.EXPECTED_OUTCOME.value());
        assertEquals("context", MultiTurnParam.CONTEXT.value());
        assertEquals("user_description", MultiTurnParam.USER_DESCRIPTION.value());
        assertEquals("retrieval_context", MultiTurnParam.RETRIEVAL_CONTEXT.value());
        assertEquals("chatbot_role", MultiTurnParam.CHATBOT_ROLE.value());
        assertEquals("tools_called", MultiTurnParam.TOOLS_CALLED.value());
        assertEquals("mcp_tools_called", MultiTurnParam.MCP_TOOLS.value());
        assertEquals("mcp_resources_called", MultiTurnParam.MCP_RESOURCES.value());
        assertEquals("mcp_prompts_called", MultiTurnParam.MCP_PROMPTS.value());
    }
}
