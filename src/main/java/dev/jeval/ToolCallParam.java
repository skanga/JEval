package dev.jeval;

public enum ToolCallParam {
    INPUT_PARAMETERS("input_parameters"),
    OUTPUT("output");

    private final String value;

    ToolCallParam(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
