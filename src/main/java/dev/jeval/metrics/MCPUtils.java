package dev.jeval.metrics;

import dev.jeval.McpServer;
import java.util.List;
import java.util.stream.Collectors;

public final class MCPUtils {
    private MCPUtils() {}

    public record AvailableMcpServersBlock(String availableTools, String availableResources, String availablePrompts) {}

    public static String indentMultilineString(Object value) {
        return indentMultilineString(value, 4);
    }

    public static String indentMultilineString(Object value, int indentLevel) {
        var indent = " ".repeat(indentLevel);
        return String.valueOf(value).lines()
                .map(line -> indent + line)
                .collect(Collectors.joining("\n"));
    }

    public static AvailableMcpServersBlock availableMcpServersBlock(List<McpServer> mcpServers) {
        var availableTools = new StringBuilder();
        var availableResources = new StringBuilder();
        var availablePrompts = new StringBuilder();
        for (var mcpServer : mcpServers) {
            var header = "MCP Server " + mcpServer.serverName() + "\n";
            availableTools.append(header);
            availableResources.append(header);
            availablePrompts.append(header);
            appendBlock(availableTools, "Available Tools", mcpServer.availableTools());
            appendBlock(availableResources, "Available Resources", mcpServer.availableResources());
            appendBlock(availablePrompts, "Available Prompts", mcpServer.availablePrompts());
        }
        return new AvailableMcpServersBlock(
                availableTools.toString(),
                availableResources.toString(),
                availablePrompts.toString());
    }

    public static String taskStepsTakenText(MCPTaskCompletionMetric.Task task) {
        return String.join("\n\n", task.stepsTaken());
    }

    private static void appendBlock(StringBuilder builder, String title, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append("\n").append(title).append(":\n[\n");
        for (var i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(",\n");
            }
            builder.append(indentMultilineString(values.get(i)));
        }
        builder.append("\n]");
    }
}
