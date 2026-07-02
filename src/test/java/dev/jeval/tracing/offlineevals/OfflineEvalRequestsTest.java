package dev.jeval.tracing.offlineevals;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OfflineEvalRequestsTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void threadRequestUsesDeepEvalAliasesAndOptionalChatbotRole() throws Exception {
        var request = new OfflineEvalRequests.EvaluateThreadRequestBody("metrics", true, "support bot");

        var json = MAPPER.readTree(MAPPER.writeValueAsString(request));
        var parsed = MAPPER.readValue(
                """
                {"metric_collection":"metrics","overwrite_metrics":false}
                """,
                OfflineEvalRequests.EvaluateThreadRequestBody.class);

        assertAll(
                () -> assertEquals("metrics", json.get("metricCollection").asText()),
                () -> assertEquals(true, json.get("overwriteMetrics").asBoolean()),
                () -> assertEquals("support bot", json.get("chatbotRole").asText()),
                () -> assertFalse(json.has("metric_collection")),
                () -> assertEquals(new OfflineEvalRequests.EvaluateThreadRequestBody("metrics", false, null), parsed));
    }

    @Test
    void traceAndSpanRequestsUseDeepEvalAliases() throws Exception {
        var traceJson = MAPPER.readTree(MAPPER.writeValueAsString(
                new OfflineEvalRequests.EvaluateTraceRequestBody("trace-metrics", true)));
        var spanJson = MAPPER.readTree(MAPPER.writeValueAsString(
                new OfflineEvalRequests.EvaluateSpanRequestBody("span-metrics", false)));

        assertAll(
                () -> assertEquals("trace-metrics", traceJson.get("metricCollection").asText()),
                () -> assertEquals(true, traceJson.get("overwriteMetrics").asBoolean()),
                () -> assertEquals("span-metrics", spanJson.get("metricCollection").asText()),
                () -> assertEquals(false, spanJson.get("overwriteMetrics").asBoolean()));
    }

    @Test
    void metricCollectionIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new OfflineEvalRequests.EvaluateTraceRequestBody(" ", true));
    }
}
