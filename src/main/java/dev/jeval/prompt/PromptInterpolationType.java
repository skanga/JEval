package dev.jeval.prompt;

public enum PromptInterpolationType {
    MUSTACHE("MUSTACHE"),
    MUSTACHE_WITH_SPACE("MUSTACHE_WITH_SPACE"),
    FSTRING("FSTRING"),
    DOLLAR_BRACKETS("DOLLAR_BRACKETS"),
    JINJA("JINJA");

    private final String value;

    PromptInterpolationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
