package dev.jeval.annotation;

import java.util.LinkedHashMap;
import java.util.Map;

public record ApiAnnotation(
        int rating,
        String traceUuid,
        String spanUuid,
        String threadId,
        String expectedOutput,
        String expectedOutcome,
        String explanation,
        AnnotationType type,
        String userId) {

    public ApiAnnotation {
        var targetCount = present(traceUuid) + present(spanUuid) + present(threadId);
        if (targetCount > 1) {
            throw new IllegalArgumentException("Only one of 'traceUuid', 'spanUuid', or 'threadId' should be provided.");
        }
        if (targetCount == 0) {
            throw new IllegalArgumentException("One of 'traceUuid', 'spanUuid', or 'threadId' must be provided.");
        }
        if (type == AnnotationType.FIVE_STAR_RATING && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("Five star rating must be between 1 and 5.");
        }
        if (type == AnnotationType.THUMBS_RATING && (rating < 0 || rating > 1)) {
            throw new IllegalArgumentException("Thumbs rating must be either 0 or 1.");
        }
        if (present(threadId) == 1 && expectedOutput != null) {
            throw new IllegalArgumentException("Expected output cannot be provided for threads.");
        }
        if (present(threadId) == 0 && expectedOutcome != null) {
            throw new IllegalArgumentException("Expected outcome cannot be provided for traces or spans.");
        }
    }

    public Map<String, Object> toRequestBody() {
        var body = new LinkedHashMap<String, Object>();
        put(body, "rating", rating);
        put(body, "traceUuid", traceUuid);
        put(body, "spanUuid", spanUuid);
        put(body, "threadId", threadId);
        put(body, "expectedOutput", expectedOutput);
        put(body, "expectedOutcome", expectedOutcome);
        put(body, "explanation", explanation);
        put(body, "type", type == null ? null : type.value());
        put(body, "userId", userId);
        return Map.copyOf(body);
    }

    private static int present(String value) {
        return value == null || value.isEmpty() ? 0 : 1;
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) {
            body.put(key, value);
        }
    }
}
