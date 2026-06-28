package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpTest {

    @Test
    void toolPromptAndResourceCallsKeepDeepEvalFields() {
        var args = new HashMap<String, Object>(Map.of("query", "refund"));

        var tool = new McpToolCall("search", args, "ok");
        var prompt = new McpPromptCall("summarize", "prompt result");
        var resource = new McpResourceCall("file://policy", "resource result");
        args.put("query", "mutated");

        assertAll(
                () -> assertEquals("search", tool.name()),
                () -> assertEquals(Map.of("query", "refund"), tool.args()),
                () -> assertEquals("ok", tool.result()),
                () -> assertEquals("summarize", prompt.name()),
                () -> assertEquals("prompt result", prompt.result()),
                () -> assertEquals("file://policy/", resource.uri()),
                () -> assertEquals("resource result", resource.result()));
    }

    @Test
    void toolCallArgsAllowNullValues() {
        var args = new HashMap<String, Object>();
        args.put("optional", null);

        var tool = new McpToolCall("search", args, "ok");

        assertEquals(args, tool.args());
    }

    @Test
    void serverCopiesListsAndValidatesTransport() {
        var tools = new ArrayList<>(List.of("search"));

        var server = new McpServer("policy", "streamable-http", tools, null, null);
        tools.add("mutated");

        assertAll(
                () -> assertEquals("policy", server.serverName()),
                () -> assertEquals("streamable-http", server.transport()),
                () -> assertEquals(List.of("search"), server.availableTools()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new McpServer("policy", "websocket", null, null, null)));
    }

    @Test
    void resourceCallRequiresUrlLikeUri() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new McpResourceCall("not a url", "result")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new McpResourceCall("http://", "result")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new McpResourceCall("http://?q", "result")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new McpResourceCall("policy", "result")));
    }

    @Test
    void resourceCallNormalizesFileSchemeOnlyUriLikePydanticAnyUrl() {
        var resource = assertDoesNotThrow(() -> new McpResourceCall("file:", "result"));

        assertEquals("file:///", resource.uri());
    }
}
