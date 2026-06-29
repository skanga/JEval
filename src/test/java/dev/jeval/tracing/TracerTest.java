package dev.jeval.tracing;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.LlmTestCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TracerTest {

    @Test
    void observeCapturesNestedSpansAsTraceMap() {
        var tracer = new Tracer();

        var output = tracer.observe("agent", "agent", Map.of("input", "refund"), () -> {
            var toolOutput = tracer.observe("lookup", "tool", Map.of("query", "refund"), () -> "policy");
            return "answer: " + toolOutput;
        });

        var trace = tracer.trace();
        var testCase = LlmTestCase.builder("refund")
                .actualOutput(output)
                .trace(trace)
                .build();

        @SuppressWarnings("unchecked")
        var children = (List<Map<String, Object>>) trace.get("children");
        var child = children.getFirst();

        assertAll(
                () -> assertEquals("answer: policy", output),
                () -> assertEquals("agent", trace.get("name")),
                () -> assertEquals("agent", trace.get("type")),
                () -> assertEquals("answer: policy", trace.get("output")),
                () -> assertEquals("refund", ((Map<?, ?>) trace.get("metadata")).get("input")),
                () -> assertEquals(1, children.size()),
                () -> assertEquals("lookup", child.get("name")),
                () -> assertEquals("tool", child.get("type")),
                () -> assertEquals("policy", child.get("output")),
                () -> assertEquals(trace, testCase.trace()));
    }

    @Test
    void observeRecordsErrorsAndRethrows() {
        var tracer = new Tracer();

        var thrown = assertThrows(IllegalStateException.class,
                () -> tracer.observe("agent", () -> {
                    throw new IllegalStateException("boom");
                }));

        var trace = tracer.trace();
        @SuppressWarnings("unchecked")
        var error = (Map<String, Object>) trace.get("error");

        assertAll(
                () -> assertEquals("boom", thrown.getMessage()),
                () -> assertEquals("agent", trace.get("name")),
                () -> assertEquals("java.lang.IllegalStateException", error.get("type")),
                () -> assertEquals("boom", error.get("message")));
    }

    @Test
    void invokeObservedUsesObserveAnnotationNameAndType() {
        var tracer = new Tracer();
        var service = new AnnotatedService();

        var output = tracer.invokeObserved(service, "answer", new Class<?>[] {String.class}, "refund");

        var trace = tracer.trace();

        assertAll(
                () -> assertEquals("answer: refund", output),
                () -> assertEquals("answerer", trace.get("name")),
                () -> assertEquals("llm", trace.get("type")));
    }

    @Test
    void traceRequiresAtLeastOneObservedSpan() {
        var tracer = new Tracer();

        var thrown = assertThrows(IllegalStateException.class, tracer::trace);

        assertTrue(thrown.getMessage().contains("No trace"));
    }

    private static final class AnnotatedService {
        @Observe(name = "answerer", type = "llm")
        String answer(String input) {
            return "answer: " + input;
        }
    }
}
