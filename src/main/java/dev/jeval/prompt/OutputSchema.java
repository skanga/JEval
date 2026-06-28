package dev.jeval.prompt;

import java.util.List;

public record OutputSchema(String id, List<OutputSchemaField> fields, String name) {
    public OutputSchema {
        fields = fields == null ? null : List.copyOf(fields);
    }
}
