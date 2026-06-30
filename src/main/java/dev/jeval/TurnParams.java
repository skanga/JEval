package dev.jeval;

/**
 * @deprecated Use {@link MultiTurnParam}. This class preserves DeepEval's
 * deprecated TurnParams compatibility alias.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class TurnParams {
    public static final MultiTurnParam ROLE = MultiTurnParam.ROLE;
    public static final MultiTurnParam CONTENT = MultiTurnParam.CONTENT;
    public static final MultiTurnParam METADATA = MultiTurnParam.METADATA;
    public static final MultiTurnParam TAGS = MultiTurnParam.TAGS;
    public static final MultiTurnParam SCENARIO = MultiTurnParam.SCENARIO;
    public static final MultiTurnParam EXPECTED_OUTCOME = MultiTurnParam.EXPECTED_OUTCOME;
    public static final MultiTurnParam CONTEXT = MultiTurnParam.CONTEXT;
    public static final MultiTurnParam USER_DESCRIPTION = MultiTurnParam.USER_DESCRIPTION;
    public static final MultiTurnParam RETRIEVAL_CONTEXT = MultiTurnParam.RETRIEVAL_CONTEXT;
    public static final MultiTurnParam CHATBOT_ROLE = MultiTurnParam.CHATBOT_ROLE;
    public static final MultiTurnParam TOOLS_CALLED = MultiTurnParam.TOOLS_CALLED;
    public static final MultiTurnParam MCP_TOOLS = MultiTurnParam.MCP_TOOLS;
    public static final MultiTurnParam MCP_RESOURCES = MultiTurnParam.MCP_RESOURCES;
    public static final MultiTurnParam MCP_PROMPTS = MultiTurnParam.MCP_PROMPTS;

    private TurnParams() {}
}
