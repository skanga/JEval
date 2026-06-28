package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolTest {

    @Test
    void keepsDeepEvalToolFields() {
        var schema = new OutputSchema(
                "schema-1",
                List.of(new OutputSchemaField("field-1", SchemaDataType.STRING, "query", null, true, null)),
                "SearchInput");

        var tool = new Tool("tool-1", "search", "Search documents", ToolMode.STRICT, schema);

        assertEquals("tool-1", tool.id());
        assertEquals("search", tool.name());
        assertEquals("Search documents", tool.description());
        assertEquals(ToolMode.STRICT, tool.mode());
        assertEquals(schema, tool.structuredSchema());
    }

    @Test
    void constructorGeneratesIdWhenOmitted() {
        var tool = new Tool("search", "Search documents", ToolMode.ALLOW_ADDITIONAL, null);

        assertFalse(tool.id().isBlank());
        assertEquals("search", tool.name());
    }
}
