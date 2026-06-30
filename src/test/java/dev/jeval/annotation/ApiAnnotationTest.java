package dev.jeval.annotation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ApiAnnotationTest {

    @Test
    void annotationTypesUseDeepEvalWireValues() {
        assertAll(
                () -> assertEquals("THUMBS_RATING", AnnotationType.THUMBS_RATING.value()),
                () -> assertEquals("FIVE_STAR_RATING", AnnotationType.FIVE_STAR_RATING.value()));
    }

    @Test
    void requestBodyUsesDeepEvalAliasesAndExcludesNulls() {
        var annotation = new ApiAnnotation(
                1,
                "trace-1",
                null,
                null,
                "expected answer",
                null,
                "looks right",
                AnnotationType.THUMBS_RATING,
                "user-1");

        var body = annotation.toRequestBody();

        assertAll(
                () -> assertEquals(1, body.get("rating")),
                () -> assertEquals("trace-1", body.get("traceUuid")),
                () -> assertEquals("expected answer", body.get("expectedOutput")),
                () -> assertEquals("looks right", body.get("explanation")),
                () -> assertEquals("THUMBS_RATING", body.get("type")),
                () -> assertEquals("user-1", body.get("userId")),
                () -> assertFalse(body.containsKey("spanUuid")),
                () -> assertFalse(body.containsKey("threadId")),
                () -> assertFalse(body.containsKey("expectedOutcome")));
    }

    @Test
    void validatesExactlyOneAnnotationTargetLikeDeepEval() {
        assertAll(
                () -> assertEquals(
                        "One of 'traceUuid', 'spanUuid', or 'threadId' must be provided.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(1, null, null, null, null, null, null,
                                        AnnotationType.THUMBS_RATING, null)).getMessage()),
                () -> assertEquals(
                        "Only one of 'traceUuid', 'spanUuid', or 'threadId' should be provided.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(1, "trace-1", "span-1", "thread-1", null, null, null,
                                        AnnotationType.THUMBS_RATING, null)).getMessage()));
    }

    @Test
    void treatsEmptyTargetIdsAsMissingLikeDeepEval() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> new ApiAnnotation(1, "", null, null, null, null, null, AnnotationType.THUMBS_RATING, null));

        assertEquals("One of 'traceUuid', 'spanUuid', or 'threadId' must be provided.", error.getMessage());
    }

    @Test
    void validatesRatingBoundsLikeDeepEval() {
        assertAll(
                () -> assertEquals("Thumbs rating must be either 0 or 1.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(2, "trace-1", null, null, null, null, null,
                                        AnnotationType.THUMBS_RATING, null)).getMessage()),
                () -> assertEquals("Five star rating must be between 1 and 5.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(0, "trace-1", null, null, null, null, null,
                                        AnnotationType.FIVE_STAR_RATING, null)).getMessage()));
    }

    @Test
    void validatesExpectedOutputAndOutcomeTargetsLikeDeepEval() {
        assertAll(
                () -> assertEquals("Expected output cannot be provided for threads.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(1, null, null, "thread-1", "expected", null, null,
                                        AnnotationType.THUMBS_RATING, null)).getMessage()),
                () -> assertEquals("Expected outcome cannot be provided for traces or spans.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new ApiAnnotation(1, "trace-1", null, null, null, "outcome", null,
                                        AnnotationType.THUMBS_RATING, null)).getMessage()));
    }
}
