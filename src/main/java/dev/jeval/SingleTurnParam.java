package dev.jeval;

public enum SingleTurnParam {
    INPUT("input"),
    ACTUAL_OUTPUT("actual_output"),
    EXPECTED_OUTPUT("expected_output"),
    CONTEXT("context"),
    RETRIEVAL_CONTEXT("retrieval_context"),
    METADATA("metadata"),
    TAGS("tags"),
    TOOLS_CALLED("tools_called"),
    EXPECTED_TOOLS("expected_tools"),
    MCP_SERVERS("mcp_servers"),
    MCP_TOOLS_CALLED("mcp_tools_called"),
    MCP_RESOURCES_CALLED("mcp_resources_called"),
    MCP_PROMPTS_CALLED("mcp_prompts_called");

    private final String value;

    SingleTurnParam(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
