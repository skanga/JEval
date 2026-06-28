package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutputSchemaTest {

    @Test
    void outputSchemaFieldKeepsDeepEvalFields() {
        var field = new OutputSchemaField(
                "field-1",
                SchemaDataType.STRING,
                "answer",
                "The final answer",
                true,
                "parent-1");

        assertEquals("field-1", field.id());
        assertEquals(SchemaDataType.STRING, field.type());
        assertEquals("answer", field.name());
        assertEquals("The final answer", field.description());
        assertEquals(true, field.required());
        assertEquals("parent-1", field.parentId());
    }

    @Test
    void outputSchemaCopiesFields() {
        var fields = new ArrayList<>(List.of(new OutputSchemaField(
                "field-1",
                SchemaDataType.INTEGER,
                "score",
                null,
                false,
                null)));

        var schema = new OutputSchema("schema-1", fields, "ScoreSchema");
        fields.add(new OutputSchemaField("field-2", SchemaDataType.STRING, "reason", null, false, null));

        assertEquals("schema-1", schema.id());
        assertEquals("ScoreSchema", schema.name());
        assertEquals(1, schema.fields().size());
        assertThrows(UnsupportedOperationException.class,
                () -> schema.fields().add(new OutputSchemaField("field-3", SchemaDataType.NULL, "empty", null, false, null)));
    }
}
