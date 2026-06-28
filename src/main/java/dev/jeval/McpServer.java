package dev.jeval;

import java.util.List;
import java.util.Set;

public record McpServer(
        String serverName,
        String transport,
        List<?> availableTools,
        List<?> availableResources,
        List<?> availablePrompts) {
    private static final Set<String> TRANSPORTS = Set.of("stdio", "sse", "streamable-http");

    public McpServer(String serverName) {
        this(serverName, null, null, null, null);
    }

    public McpServer {
        if (serverName == null) {
            throw new IllegalArgumentException("'server_name' must be a string");
        }
        if (transport != null && !TRANSPORTS.contains(transport)) {
            throw new IllegalArgumentException("'transport' must be 'stdio', 'sse', or 'streamable-http'");
        }
        availableTools = availableTools == null ? null : List.copyOf(availableTools);
        availableResources = availableResources == null ? null : List.copyOf(availableResources);
        availablePrompts = availablePrompts == null ? null : List.copyOf(availablePrompts);
    }
}
