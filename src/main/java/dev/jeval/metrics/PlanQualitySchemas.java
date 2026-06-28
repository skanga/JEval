package dev.jeval.metrics;

import java.util.List;

public final class PlanQualitySchemas {
    private PlanQualitySchemas() {
    }

    public static AgentPlan parseAgentPlan(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new AgentPlan(MetricUtils.requiredStringList(node, "plan"));
    }

    public static PlanQualityScore parseScore(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new PlanQualityScore(MetricUtils.requiredDouble(node, "score"), MetricUtils.requiredText(node, "reason"));
    }

    public record AgentPlan(List<String> plan) {
        public AgentPlan {
            plan = plan == null ? List.of() : List.copyOf(plan);
        }
    }

    public record PlanQualityScore(double score, String reason) {
    }
}
