package dev.jeval.metrics;

import java.util.ArrayList;
import java.util.List;

public final class RoleViolationSchemas {
    private RoleViolationSchemas() {
    }

    public static RoleViolations parseRoleViolations(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new RoleViolations(MetricUtils.requiredStringList(node, "role_violations"));
    }

    public static Verdicts parseVerdicts(String modelOutput) {
        var node = MetricUtils.required(MetricUtils.trimAndLoadJson(modelOutput), "verdicts");
        if (!node.isArray()) {
            throw new IllegalArgumentException("Schema field must be a list: verdicts");
        }
        var values = new ArrayList<RoleViolationVerdict>();
        node.forEach(value -> values.add(new RoleViolationVerdict(
                MetricUtils.requiredText(value, "verdict"),
                MetricUtils.requiredText(value, "reason"))));
        return new Verdicts(values);
    }

    public static RoleViolationScoreReason parseScoreReason(String modelOutput) {
        var node = MetricUtils.trimAndLoadJson(modelOutput);
        return new RoleViolationScoreReason(MetricUtils.requiredText(node, "reason"));
    }

    public record RoleViolations(List<String> roleViolations) {
        public RoleViolations {
            roleViolations = roleViolations == null ? null : List.copyOf(roleViolations);
        }
    }

    public record RoleViolationVerdict(String verdict, String reason) {
    }

    public record Verdicts(List<RoleViolationVerdict> verdicts) {
        public Verdicts {
            verdicts = verdicts == null ? null : List.copyOf(verdicts);
        }
    }

    public record RoleViolationScoreReason(String reason) {
    }
}
