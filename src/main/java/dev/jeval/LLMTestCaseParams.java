package dev.jeval;

/**
 * @deprecated Use {@link SingleTurnParam}. This class preserves DeepEval's
 * deprecated LLMTestCaseParams compatibility alias.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public final class LLMTestCaseParams {
    public static final SingleTurnParam INPUT = SingleTurnParam.INPUT;
    public static final SingleTurnParam ACTUAL_OUTPUT = SingleTurnParam.ACTUAL_OUTPUT;
    public static final SingleTurnParam EXPECTED_OUTPUT = SingleTurnParam.EXPECTED_OUTPUT;
    public static final SingleTurnParam CONTEXT = SingleTurnParam.CONTEXT;
    public static final SingleTurnParam RETRIEVAL_CONTEXT = SingleTurnParam.RETRIEVAL_CONTEXT;
    public static final SingleTurnParam METADATA = SingleTurnParam.METADATA;
    public static final SingleTurnParam TAGS = SingleTurnParam.TAGS;
    public static final SingleTurnParam TOOLS_CALLED = SingleTurnParam.TOOLS_CALLED;
    public static final SingleTurnParam EXPECTED_TOOLS = SingleTurnParam.EXPECTED_TOOLS;
    public static final SingleTurnParam MCP_SERVERS = SingleTurnParam.MCP_SERVERS;
    public static final SingleTurnParam MCP_TOOLS_CALLED = SingleTurnParam.MCP_TOOLS_CALLED;
    public static final SingleTurnParam MCP_RESOURCES_CALLED = SingleTurnParam.MCP_RESOURCES_CALLED;
    public static final SingleTurnParam MCP_PROMPTS_CALLED = SingleTurnParam.MCP_PROMPTS_CALLED;

    private LLMTestCaseParams() {}
}
