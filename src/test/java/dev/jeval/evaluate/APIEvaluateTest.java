package dev.jeval.evaluate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class APIEvaluateTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void readsDeepEvalCamelCaseAliases() throws Exception {
        var api = JSON.readValue("""
                {
                  "metricCollection": "rag",
                  "llmTestCases": [{"input": "q", "actualOutput": "a"}],
                  "conversationalTestCases": null
                }
                """, APIEvaluate.class);

        assertEquals("rag", api.metricCollection());
        assertEquals(1, api.llmTestCases().size());
        assertEquals("q", api.llmTestCases().getFirst().input());
        assertEquals("a", api.llmTestCases().getFirst().actualOutput());
        assertEquals(null, api.conversationalTestCases());
    }

    @Test
    void requiresMetricCollectionLikeDeepEval() {
        assertThrows(IllegalArgumentException.class,
                () -> new APIEvaluate(null, null, null));
    }
}
