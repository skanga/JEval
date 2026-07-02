package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jeval.McpServer;
import dev.jeval.metrics.MCPTaskCompletionMetric.Task;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MCPUtilsTest {

    @Test
    void indentsEveryLineLikeDeepEval() {
        assertAll(
                () -> assertEquals("    alpha\n    beta", MCPUtils.indentMultilineString("alpha\nbeta")),
                () -> assertEquals("  alpha", MCPUtils.indentMultilineString("alpha", 2)),
                () -> assertEquals("", MCPUtils.indentMultilineString("")));
    }

    @Test
    void formatsAvailableMcpServerBlocksLikeDeepEval() {
        var blocks = MCPUtils.availableMcpServersBlock(List.of(
                new McpServer(
                        "policy",
                        null,
                        List.of(Map.of("name", "search\nadvanced")),
                        null,
                        List.of("summarize"))));

        assertAll(
                () -> assertEquals("""
                        MCP Server policy

                        Available Tools:
                        [
                            {name=search
                            advanced}
                        ]""", blocks.availableTools()),
                () -> assertEquals("MCP Server policy\n", blocks.availableResources()),
                () -> assertEquals("""
                        MCP Server policy

                        Available Prompts:
                        [
                            summarize
                        ]""", blocks.availablePrompts()));
    }

    @Test
    void joinsTaskStepsTakenLikeDeepEval() {
        assertEquals("step one\n\nstep two", MCPUtils.taskStepsTakenText(new Task("task", List.of("step one", "step two"))));
    }
}
