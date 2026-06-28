package dev.jeval.metrics.dag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.MultiTurnParam;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConversationalDeepAcyclicGraph {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<ConversationalDagNode> rootNodes;

    public ConversationalDeepAcyclicGraph(List<? extends ConversationalDagNode> rootNodes) {
        if (rootNodes.isEmpty()) {
            throw new IllegalArgumentException("rootNodes cannot be empty");
        }
        if (rootNodes.size() > 1 && rootNodes.stream().anyMatch(ConversationalDeepAcyclicGraph::isJudgementNode)) {
            throw new IllegalArgumentException("multiple conversational judgement roots are not allowed");
        }
        this.rootNodes = List.copyOf(rootNodes);
    }

    public List<ConversationalDagNode> rootNodes() {
        return rootNodes;
    }

    public boolean multiturn() {
        return true;
    }

    public Map<String, Object> toMap() {
        var ordered = walkNodes();
        var ids = new IdentityHashMap<ConversationalDagNode, String>();
        ordered.forEach(node -> ids.put(node, UUID.randomUUID().toString()));

        var nodes = new LinkedHashMap<String, Object>();
        for (var node : ordered) {
            nodes.put(ids.get(node), serialize(node, ids));
        }
        return Map.of("nodes", nodes);
    }

    public String toJson() {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toMap());
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Could not serialize conversational DAG.", error);
        }
    }

    public static ConversationalDeepAcyclicGraph fromJson(String json) {
        try {
            return fromMap(MAPPER.readValue(json, new TypeReference<>() {}));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid conversational DAG JSON.", error);
        }
    }

    public static ConversationalDeepAcyclicGraph fromMap(Map<String, Object> data) {
        var specs = nodesSpec(data);
        var referenced = referencedIds(specs);
        var roots = specs.keySet().stream()
                .filter(id -> !referenced.contains(id))
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("No root nodes detected.");
        }
        var built = new LinkedHashMap<String, ConversationalDagNode>();
        return new ConversationalDeepAcyclicGraph(roots.stream()
                .map(id -> build(id, specs, built, new LinkedHashSet<>()))
                .toList());
    }

    private static boolean isJudgementNode(ConversationalDagNode node) {
        return node instanceof ConversationalBinaryJudgementNode
                || node instanceof ConversationalNonBinaryJudgementNode;
    }

    private List<ConversationalDagNode> walkNodes() {
        var ordered = new ArrayList<ConversationalDagNode>();
        var seen = new IdentityHashMap<ConversationalDagNode, Boolean>();
        var queue = new ArrayDeque<>(rootNodes);
        while (!queue.isEmpty()) {
            var node = queue.removeFirst();
            if (seen.put(node, true) != null) {
                continue;
            }
            ordered.add(node);
            children(node).forEach(queue::addLast);
        }
        return ordered;
    }

    private static List<? extends ConversationalDagNode> children(ConversationalDagNode node) {
        return switch (node) {
            case ConversationalTaskNode task -> task.children();
            case ConversationalBinaryJudgementNode judgement -> judgement.children();
            case ConversationalNonBinaryJudgementNode judgement -> judgement.children();
            case ConversationalVerdictNode verdict when verdict.child() != null -> List.of(verdict.child());
            default -> List.of();
        };
    }

    private static Map<String, Object> serialize(
            ConversationalDagNode node, IdentityHashMap<ConversationalDagNode, String> ids) {
        var out = new LinkedHashMap<String, Object>();
        switch (node) {
            case ConversationalTaskNode task -> {
                out.put("type", "TaskNode");
                out.put("instructions", task.instructions());
                out.put("output_label", task.outputLabel());
                out.put("label", task.label());
                out.put("evaluation_params", params(task.evaluationParams()));
                putTurnWindow(out, task.turnWindow());
                out.put("children", task.children().stream().map(ids::get).toList());
            }
            case ConversationalBinaryJudgementNode judgement -> {
                out.put("type", "BinaryJudgementNode");
                out.put("criteria", judgement.criteria());
                out.put("label", judgement.label());
                out.put("evaluation_params", params(judgement.evaluationParams()));
                putTurnWindow(out, judgement.turnWindow());
                out.put("children", judgement.children().stream().map(ids::get).toList());
            }
            case ConversationalNonBinaryJudgementNode judgement -> {
                out.put("type", "NonBinaryJudgementNode");
                out.put("criteria", judgement.criteria());
                out.put("label", judgement.label());
                out.put("evaluation_params", params(judgement.evaluationParams()));
                putTurnWindow(out, judgement.turnWindow());
                out.put("children", judgement.children().stream().map(ids::get).toList());
            }
            case ConversationalVerdictNode verdict -> {
                out.put("type", "VerdictNode");
                out.put("verdict", verdict.verdict());
                if (verdict.score() != null) {
                    out.put("score", verdict.score());
                } else if (verdict.metric() != null) {
                    throw new IllegalArgumentException("Metric verdict children cannot be serialized.");
                } else {
                    out.put("child", Map.of("type", "node", "ref", ids.get(verdict.child())));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported DAG node: " + node.getClass().getSimpleName());
        }
        return out;
    }

    private static List<String> params(List<MultiTurnParam> params) {
        return params.stream().map(MultiTurnParam::value).toList();
    }

    private static void putTurnWindow(Map<String, Object> out, TurnWindow turnWindow) {
        if (turnWindow != null) {
            out.put("turn_window", List.of(turnWindow.start(), turnWindow.end()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> nodesSpec(Map<String, Object> data) {
        if (data == null || !(data.get("nodes") instanceof Map<?, ?> nodes) || nodes.isEmpty()) {
            throw new IllegalArgumentException("DAG document requires non-empty nodes.");
        }
        return (Map<String, Map<String, Object>>) nodes;
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashSet<String> referencedIds(Map<String, Map<String, Object>> specs) {
        var referenced = new LinkedHashSet<String>();
        for (var spec : specs.values()) {
            var children = (List<String>) spec.getOrDefault("children", List.of());
            referenced.addAll(children);
            if (spec.get("child") instanceof Map<?, ?> child && "node".equals(child.get("type"))) {
                referenced.add((String) child.get("ref"));
            }
        }
        return referenced;
    }

    @SuppressWarnings("unchecked")
    private static ConversationalDagNode build(
            String id,
            Map<String, Map<String, Object>> specs,
            Map<String, ConversationalDagNode> built,
            LinkedHashSet<String> stack) {
        if (built.containsKey(id)) {
            return built.get(id);
        }
        if (!stack.add(id)) {
            throw new IllegalArgumentException("Cycle detected in DAG JSON.");
        }
        var spec = specs.get(id);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown DAG node id: " + id);
        }
        var type = (String) spec.get("type");
        ConversationalDagNode node = switch (type) {
            case "TaskNode" -> new ConversationalTaskNode(
                    (String) spec.get("instructions"),
                    (String) spec.get("output_label"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    parseTurnWindow(spec.get("turn_window")),
                    (String) spec.get("label"));
            case "BinaryJudgementNode" -> new ConversationalBinaryJudgementNode(
                    (String) spec.get("criteria"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> (ConversationalVerdictNode) build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    parseTurnWindow(spec.get("turn_window")),
                    (String) spec.get("label"));
            case "NonBinaryJudgementNode" -> new ConversationalNonBinaryJudgementNode(
                    (String) spec.get("criteria"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> (ConversationalVerdictNode) build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    parseTurnWindow(spec.get("turn_window")),
                    (String) spec.get("label"));
            case "VerdictNode" -> buildVerdict(spec, specs, built, stack);
            default -> throw new IllegalArgumentException("Unknown DAG node type: " + type);
        };
        stack.remove(id);
        built.put(id, node);
        return node;
    }

    @SuppressWarnings("unchecked")
    private static ConversationalVerdictNode buildVerdict(
            Map<String, Object> spec,
            Map<String, Map<String, Object>> specs,
            Map<String, ConversationalDagNode> built,
            LinkedHashSet<String> stack) {
        var verdict = spec.get("verdict");
        if (spec.containsKey("score")) {
            return new ConversationalVerdictNode(verdict, ((Number) spec.get("score")).intValue());
        }
        var child = (Map<String, Object>) spec.get("child");
        if (child == null || !"node".equals(child.get("type"))) {
            throw new IllegalArgumentException("Only node verdict children are supported.");
        }
        return new ConversationalVerdictNode(verdict, null, build((String) child.get("ref"), specs, built, stack));
    }

    @SuppressWarnings("unchecked")
    private static List<MultiTurnParam> parseParams(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<String>) value).stream()
                .map(ConversationalDeepAcyclicGraph::param)
                .toList();
    }

    private static MultiTurnParam param(String value) {
        for (var param : MultiTurnParam.values()) {
            if (param.value().equals(value)) {
                return param;
            }
        }
        throw new IllegalArgumentException("Unknown evaluation_param: " + value);
    }

    private static TurnWindow parseTurnWindow(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?> window) || window.size() != 2) {
            throw new IllegalArgumentException("turn_window must contain start and end.");
        }
        return new TurnWindow(((Number) window.get(0)).intValue(), ((Number) window.get(1)).intValue());
    }
}
