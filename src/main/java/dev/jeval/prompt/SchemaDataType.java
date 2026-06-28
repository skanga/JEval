package dev.jeval.prompt;

public enum SchemaDataType {
    OBJECT("OBJECT"),
    ARRAY("ARRAY"),
    STRING("STRING"),
    FLOAT("FLOAT"),
    INTEGER("INTEGER"),
    BOOLEAN("BOOLEAN"),
    NULL("NULL");

    private final String value;

    SchemaDataType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
