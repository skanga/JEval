package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertAll;
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

    @Test
    void rejectsMissingRequiredFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchema(null, List.of(), "ScoreSchema")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchema(" ", List.of(), "ScoreSchema")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchema("schema-1", List.of(), null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchema("schema-1", List.of(), " ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchemaField(null, SchemaDataType.STRING, "answer", null, true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchemaField(" ", SchemaDataType.STRING, "answer", null, true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchemaField("field-1", null, "answer", null, true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchemaField("field-1", SchemaDataType.STRING, null, null, true, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new OutputSchemaField("field-1", SchemaDataType.STRING, " ", null, true, null)));
    }
}
