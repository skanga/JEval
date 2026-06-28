package dev.jeval.metrics.dag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.SingleTurnParam;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeepAcyclicGraph {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<DagNode> rootNodes;

    public DeepAcyclicGraph(List<? extends DagNode> rootNodes) {
        if (rootNodes.isEmpty()) {
            throw new IllegalArgumentException("rootNodes cannot be empty");
        }
        if (rootNodes.size() > 1 && rootNodes.stream().anyMatch(DeepAcyclicGraph::isJudgementNode)) {
            throw new IllegalArgumentException("multiple judgement roots are not allowed");
        }
        this.rootNodes = List.copyOf(rootNodes);
    }

    public List<DagNode> rootNodes() {
        return rootNodes;
    }

    public boolean multiturn() {
        return false;
    }

    public Map<String, Object> toMap() {
        var ordered = walkNodes();
        var ids = new IdentityHashMap<DagNode, String>();
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
            throw new IllegalArgumentException("Could not serialize DAG.", error);
        }
    }

    public static DeepAcyclicGraph fromJson(String json) {
        try {
            return fromMap(MAPPER.readValue(json, new TypeReference<>() {}));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Invalid DAG JSON.", error);
        }
    }

    public static DeepAcyclicGraph fromMap(Map<String, Object> data) {
        var specs = nodesSpec(data);
        var referenced = referencedIds(specs);
        var roots = specs.keySet().stream()
                .filter(id -> !referenced.contains(id))
                .toList();
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("No root nodes detected.");
        }
        var built = new LinkedHashMap<String, DagNode>();
        return new DeepAcyclicGraph(roots.stream()
                .map(id -> build(id, specs, built, new LinkedHashSet<>()))
                .toList());
    }

    private static boolean isJudgementNode(DagNode node) {
        return node instanceof BinaryJudgementNode || node instanceof NonBinaryJudgementNode;
    }

    private List<DagNode> walkNodes() {
        var ordered = new ArrayList<DagNode>();
        var seen = new IdentityHashMap<DagNode, Boolean>();
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

    private static List<DagNode> children(DagNode node) {
        if (node instanceof TaskNode task) {
            return task.children();
        }
        if (node instanceof BinaryJudgementNode judgement) {
            return List.copyOf(judgement.children());
        }
        if (node instanceof NonBinaryJudgementNode judgement) {
            return List.copyOf(judgement.children());
        }
        if (node instanceof VerdictNode verdict && verdict.child() != null) {
            return List.of(verdict.child());
        }
        return List.of();
    }

    private static Map<String, Object> serialize(DagNode node, IdentityHashMap<DagNode, String> ids) {
        var out = new LinkedHashMap<String, Object>();
        switch (node) {
            case TaskNode task -> {
                out.put("type", "TaskNode");
                out.put("instructions", task.instructions());
                out.put("output_label", task.outputLabel());
                out.put("label", task.label());
                out.put("evaluation_params", params(task.evaluationParams()));
                out.put("children", task.children().stream().map(ids::get).toList());
            }
            case BinaryJudgementNode judgement -> {
                out.put("type", "BinaryJudgementNode");
                out.put("criteria", judgement.criteria());
                out.put("label", judgement.label());
                out.put("evaluation_params", params(judgement.evaluationParams()));
                out.put("children", judgement.children().stream().map(ids::get).toList());
            }
            case NonBinaryJudgementNode judgement -> {
                out.put("type", "NonBinaryJudgementNode");
                out.put("criteria", judgement.criteria());
                out.put("label", judgement.label());
                out.put("evaluation_params", params(judgement.evaluationParams()));
                out.put("children", judgement.children().stream().map(ids::get).toList());
            }
            case VerdictNode verdict -> {
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

    private static List<String> params(List<SingleTurnParam> params) {
        return params.stream().map(SingleTurnParam::value).toList();
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
    private static DagNode build(
            String id,
            Map<String, Map<String, Object>> specs,
            Map<String, DagNode> built,
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
        DagNode node = switch (type) {
            case "TaskNode" -> new TaskNode(
                    (String) spec.get("instructions"),
                    (String) spec.get("output_label"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    (String) spec.get("label"));
            case "BinaryJudgementNode" -> new BinaryJudgementNode(
                    (String) spec.get("criteria"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> (VerdictNode) build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    (String) spec.get("label"));
            case "NonBinaryJudgementNode" -> new NonBinaryJudgementNode(
                    (String) spec.get("criteria"),
                    ((List<String>) spec.getOrDefault("children", List.of())).stream()
                            .map(child -> (VerdictNode) build(child, specs, built, stack))
                            .toList(),
                    parseParams(spec.get("evaluation_params")),
                    (String) spec.get("label"));
            case "VerdictNode" -> buildVerdict(spec, specs, built, stack);
            default -> throw new IllegalArgumentException("Unknown DAG node type: " + type);
        };
        stack.remove(id);
        built.put(id, node);
        return node;
    }

    @SuppressWarnings("unchecked")
    private static VerdictNode buildVerdict(
            Map<String, Object> spec,
            Map<String, Map<String, Object>> specs,
            Map<String, DagNode> built,
            LinkedHashSet<String> stack) {
        var verdict = spec.get("verdict");
        if (spec.containsKey("score")) {
            return new VerdictNode(verdict, ((Number) spec.get("score")).intValue());
        }
        var child = (Map<String, Object>) spec.get("child");
        if (child == null || !"node".equals(child.get("type"))) {
            throw new IllegalArgumentException("Only node verdict children are supported.");
        }
        return new VerdictNode(verdict, null, build((String) child.get("ref"), specs, built, stack));
    }

    @SuppressWarnings("unchecked")
    private static List<SingleTurnParam> parseParams(Object value) {
        if (value == null) {
            return List.of();
        }
        return ((List<String>) value).stream()
                .map(DeepAcyclicGraph::param)
                .toList();
    }

    private static SingleTurnParam param(String value) {
        for (var param : SingleTurnParam.values()) {
            if (param.value().equals(value)) {
                return param;
            }
        }
        throw new IllegalArgumentException("Unknown evaluation_param: " + value);
    }
}
