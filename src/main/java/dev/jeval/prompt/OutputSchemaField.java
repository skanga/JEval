package dev.jeval.prompt;

public record OutputSchemaField(
        String id,
        SchemaDataType type,
        String name,
        String description,
        Boolean required,
        String parentId) {
}
