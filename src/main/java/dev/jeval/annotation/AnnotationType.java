package dev.jeval.annotation;

public enum AnnotationType {
    THUMBS_RATING("THUMBS_RATING"),
    FIVE_STAR_RATING("FIVE_STAR_RATING");

    private final String value;

    AnnotationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
