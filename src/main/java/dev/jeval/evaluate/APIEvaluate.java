package dev.jeval.evaluate;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jeval.ConversationalTestCase;
import dev.jeval.LlmTestCase;
import java.util.List;

public record APIEvaluate(
        @JsonProperty("metricCollection") @JsonAlias("metric_collection") String metricCollection,
        @JsonProperty("llmTestCases") @JsonAlias("llm_test_cases") List<LlmTestCase> llmTestCases,
        @JsonProperty("conversationalTestCases") @JsonAlias("conversational_test_cases")
        List<ConversationalTestCase> conversationalTestCases) {

    public APIEvaluate {
        if (metricCollection == null || metricCollection.isBlank()) {
            throw new IllegalArgumentException("metricCollection is required");
        }
        llmTestCases = llmTestCases == null ? null : List.copyOf(llmTestCases);
        conversationalTestCases = conversationalTestCases == null ? null : List.copyOf(conversationalTestCases);
    }
}
