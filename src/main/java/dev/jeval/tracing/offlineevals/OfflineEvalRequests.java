package dev.jeval.tracing.offlineevals;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OfflineEvalRequests {
    private OfflineEvalRequests() {
    }

    public record EvaluateThreadRequestBody(
            @JsonProperty("metricCollection") @JsonAlias("metric_collection") String metricCollection,
            @JsonProperty("overwriteMetrics") @JsonAlias("overwrite_metrics") boolean overwriteMetrics,
            @JsonProperty("chatbotRole") @JsonAlias("chatbot_role") String chatbotRole) {
        public EvaluateThreadRequestBody {
            requireMetricCollection(metricCollection);
        }
    }

    public record EvaluateTraceRequestBody(
            @JsonProperty("metricCollection") @JsonAlias("metric_collection") String metricCollection,
            @JsonProperty("overwriteMetrics") @JsonAlias("overwrite_metrics") boolean overwriteMetrics) {
        public EvaluateTraceRequestBody {
            requireMetricCollection(metricCollection);
        }
    }

    public record EvaluateSpanRequestBody(
            @JsonProperty("metricCollection") @JsonAlias("metric_collection") String metricCollection,
            @JsonProperty("overwriteMetrics") @JsonAlias("overwrite_metrics") boolean overwriteMetrics) {
        public EvaluateSpanRequestBody {
            requireMetricCollection(metricCollection);
        }
    }

    private static void requireMetricCollection(String metricCollection) {
        if (metricCollection == null || metricCollection.isBlank()) {
            throw new IllegalArgumentException("metricCollection is required");
        }
    }
}
