package dev.jeval.prompt;

public record OutputSchemaField(
        String id,
        SchemaDataType type,
        String name,
        String description,
        Boolean required,
        String parentId) {
    public OutputSchemaField {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OutputSchemaField id is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("OutputSchemaField type is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("OutputSchemaField name is required");
        }
    }
}
