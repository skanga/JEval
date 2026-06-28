package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SingleTurnParamTest {

    @Test
    void valuesMatchDeepEvalSingleTurnParams() {
        assertEquals("input", SingleTurnParam.INPUT.value());
        assertEquals("actual_output", SingleTurnParam.ACTUAL_OUTPUT.value());
        assertEquals("expected_output", SingleTurnParam.EXPECTED_OUTPUT.value());
        assertEquals("context", SingleTurnParam.CONTEXT.value());
        assertEquals("retrieval_context", SingleTurnParam.RETRIEVAL_CONTEXT.value());
        assertEquals("metadata", SingleTurnParam.METADATA.value());
        assertEquals("tags", SingleTurnParam.TAGS.value());
        assertEquals("tools_called", SingleTurnParam.TOOLS_CALLED.value());
        assertEquals("expected_tools", SingleTurnParam.EXPECTED_TOOLS.value());
        assertEquals("mcp_servers", SingleTurnParam.MCP_SERVERS.value());
        assertEquals("mcp_tools_called", SingleTurnParam.MCP_TOOLS_CALLED.value());
        assertEquals("mcp_resources_called", SingleTurnParam.MCP_RESOURCES_CALLED.value());
        assertEquals("mcp_prompts_called", SingleTurnParam.MCP_PROMPTS_CALLED.value());
    }
}
