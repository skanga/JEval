package dev.jeval.metrics.dag;

import dev.jeval.SingleTurnParam;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DagUtils {
    private DagUtils() {
    }

    public static boolean isValidDagFromRoots(List<? extends DagNode> rootNodes) {
        var visited = identitySet();
        for (var root : rootNodes) {
            if (!isValidDag(root, visited, identitySet())) {
                return false;
            }
        }
        return true;
    }

    public static Set<SingleTurnParam> extractRequiredParams(List<? extends DagNode> nodes) {
        var params = new LinkedHashSet<SingleTurnParam>();
        for (var node : nodes) {
            collectParams(node, params);
        }
        return Set.copyOf(params);
    }

    public static DeepAcyclicGraph copyGraph(DeepAcyclicGraph graph) {
        var copied = new IdentityHashMap<DagNode, DagNode>();
        return new DeepAcyclicGraph(graph.rootNodes().stream()
                .map(root -> copy(root, copied))
                .toList());
    }

    private static boolean isValidDag(DagNode node, Set<DagNode> visited, Set<DagNode> stack) {
        if (stack.contains(node)) {
            return false;
        }
        if (visited.contains(node)) {
            return true;
        }
        visited.add(node);
        stack.add(node);
        for (var child : children(node)) {
            if (!isValidDag(child, visited, stack)) {
                return false;
            }
        }
        stack.remove(node);
        return true;
    }

    private static void collectParams(DagNode node, Set<SingleTurnParam> params) {
        switch (node) {
            case TaskNode task -> params.addAll(task.evaluationParams());
            case BinaryJudgementNode judgement -> params.addAll(judgement.evaluationParams());
            case NonBinaryJudgementNode judgement -> params.addAll(judgement.evaluationParams());
            default -> {
            }
        }
        for (var child : children(node)) {
            collectParams(child, params);
        }
    }

    private static DagNode copy(DagNode node, IdentityHashMap<DagNode, DagNode> copied) {
        if (copied.containsKey(node)) {
            return copied.get(node);
        }
        var clone = switch (node) {
            case TaskNode task -> new TaskNode(
                    task.instructions(),
                    task.outputLabel(),
                    task.children().stream().map(child -> copy(child, copied)).toList(),
                    task.evaluationParams(),
                    task.label());
            case BinaryJudgementNode judgement -> new BinaryJudgementNode(
                    judgement.criteria(),
                    judgement.children().stream().map(child -> (VerdictNode) copy(child, copied)).toList(),
                    judgement.evaluationParams(),
                    judgement.label());
            case NonBinaryJudgementNode judgement -> new NonBinaryJudgementNode(
                    judgement.criteria(),
                    judgement.children().stream().map(child -> (VerdictNode) copy(child, copied)).toList(),
                    judgement.evaluationParams(),
                    judgement.label());
            case VerdictNode verdict when verdict.child() != null -> new VerdictNode(
                    verdict.verdict(),
                    null,
                    copy(verdict.child(), copied));
            case VerdictNode verdict when verdict.metric() != null -> new VerdictNode(verdict.verdict(), verdict.metric());
            case VerdictNode verdict -> new VerdictNode(verdict.verdict(), verdict.score());
            default -> throw new IllegalArgumentException("Unsupported DAG node: " + node.getClass().getSimpleName());
        };
        copied.put(node, clone);
        return clone;
    }

    private static List<? extends DagNode> children(DagNode node) {
        return switch (node) {
            case TaskNode task -> task.children();
            case BinaryJudgementNode judgement -> judgement.children();
            case NonBinaryJudgementNode judgement -> judgement.children();
            case VerdictNode verdict when verdict.child() != null -> List.of(verdict.child());
            default -> List.of();
        };
    }

    private static Set<DagNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
