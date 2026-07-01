package dev.jeval.prompt;

import java.util.UUID;

public final class Tool {
    private final String id;
    private final String name;
    private final String description;
    private final ToolMode mode;
    private final OutputSchema structuredSchema;

    public Tool(String name, String description, ToolMode mode, OutputSchema structuredSchema) {
        this(UUID.randomUUID().toString(), name, description, mode, structuredSchema);
    }

    public Tool(String id, String name, String description, ToolMode mode, OutputSchema structuredSchema) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tool id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name is required");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Tool mode is required");
        }
        this.id = id;
        this.name = name;
        this.description = description;
        this.mode = mode;
        this.structuredSchema = structuredSchema;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ToolMode mode() {
        return mode;
    }

    public OutputSchema structuredSchema() {
        return structuredSchema;
    }
}
