package dev.jeval;

public enum MultiTurnParam {
    ROLE("role"),
    CONTENT("content"),
    METADATA("metadata"),
    TAGS("tags"),
    SCENARIO("scenario"),
    EXPECTED_OUTCOME("expected_outcome"),
    CONTEXT("context"),
    USER_DESCRIPTION("user_description"),
    RETRIEVAL_CONTEXT("retrieval_context"),
    CHATBOT_ROLE("chatbot_role"),
    TOOLS_CALLED("tools_called"),
    MCP_TOOLS("mcp_tools_called"),
    MCP_RESOURCES("mcp_resources_called"),
    MCP_PROMPTS("mcp_prompts_called");

    private final String value;

    MultiTurnParam(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
