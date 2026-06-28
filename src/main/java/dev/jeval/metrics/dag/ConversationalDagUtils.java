package dev.jeval.metrics.dag;

import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ConversationalDagUtils {
    private ConversationalDagUtils() {
    }

    public static boolean isValidDagFromRoots(List<? extends ConversationalDagNode> rootNodes) {
        var visited = identitySet();
        for (var root : rootNodes) {
            if (!isValidDag(root, visited, identitySet())) {
                return false;
            }
        }
        return true;
    }

    public static Set<MultiTurnParam> extractRequiredParams(List<? extends ConversationalDagNode> nodes) {
        var params = new LinkedHashSet<MultiTurnParam>();
        for (var node : nodes) {
            collectParams(node, params);
        }
        return Set.copyOf(params);
    }

    public static ConversationalDeepAcyclicGraph copyGraph(ConversationalDeepAcyclicGraph graph) {
        var copied = new IdentityHashMap<ConversationalDagNode, ConversationalDagNode>();
        return new ConversationalDeepAcyclicGraph(graph.rootNodes().stream()
                .map(root -> copy(root, copied))
                .toList());
    }

    public static void validateTurnWindows(List<? extends ConversationalDagNode> nodes, List<Turn> turns) {
        var visited = identitySet();
        for (var node : nodes) {
            validateTurnWindow(node, turns, visited);
        }
    }

    private static boolean isValidDag(
            ConversationalDagNode node, Set<ConversationalDagNode> visited, Set<ConversationalDagNode> stack) {
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

    private static void collectParams(ConversationalDagNode node, Set<MultiTurnParam> params) {
        switch (node) {
            case ConversationalTaskNode task -> params.addAll(task.evaluationParams());
            case ConversationalBinaryJudgementNode judgement -> params.addAll(judgement.evaluationParams());
            case ConversationalNonBinaryJudgementNode judgement -> params.addAll(judgement.evaluationParams());
            default -> {
            }
        }
        for (var child : children(node)) {
            collectParams(child, params);
        }
    }

    private static void validateTurnWindow(
            ConversationalDagNode node, List<Turn> turns, Set<ConversationalDagNode> visited) {
        if (!visited.add(node)) {
            return;
        }
        switch (node) {
            case ConversationalTaskNode task when task.turnWindow() != null -> task.turnWindow().validateAgainst(turns);
            case ConversationalBinaryJudgementNode judgement when judgement.turnWindow() != null ->
                    judgement.turnWindow().validateAgainst(turns);
            case ConversationalNonBinaryJudgementNode judgement when judgement.turnWindow() != null ->
                    judgement.turnWindow().validateAgainst(turns);
            default -> {
            }
        }
        for (var child : children(node)) {
            validateTurnWindow(child, turns, visited);
        }
    }

    private static ConversationalDagNode copy(
            ConversationalDagNode node, IdentityHashMap<ConversationalDagNode, ConversationalDagNode> copied) {
        if (copied.containsKey(node)) {
            return copied.get(node);
        }
        var clone = switch (node) {
            case ConversationalTaskNode task -> new ConversationalTaskNode(
                    task.instructions(),
                    task.outputLabel(),
                    task.children().stream().map(child -> copy(child, copied)).toList(),
                    task.evaluationParams(),
                    task.turnWindow(),
                    task.label());
            case ConversationalBinaryJudgementNode judgement -> new ConversationalBinaryJudgementNode(
                    judgement.criteria(),
                    judgement.children().stream()
                            .map(child -> (ConversationalVerdictNode) copy(child, copied))
                            .toList(),
                    judgement.evaluationParams(),
                    judgement.turnWindow(),
                    judgement.label());
            case ConversationalNonBinaryJudgementNode judgement -> new ConversationalNonBinaryJudgementNode(
                    judgement.criteria(),
                    judgement.children().stream()
                            .map(child -> (ConversationalVerdictNode) copy(child, copied))
                            .toList(),
                    judgement.evaluationParams(),
                    judgement.turnWindow(),
                    judgement.label());
            case ConversationalVerdictNode verdict when verdict.child() != null -> new ConversationalVerdictNode(
                    verdict.verdict(),
                    null,
                    copy(verdict.child(), copied));
            case ConversationalVerdictNode verdict when verdict.metric() != null ->
                    new ConversationalVerdictNode(verdict.verdict(), verdict.metric());
            case ConversationalVerdictNode verdict -> new ConversationalVerdictNode(verdict.verdict(), verdict.score());
            default -> throw new IllegalArgumentException("Unsupported DAG node: " + node.getClass().getSimpleName());
        };
        copied.put(node, clone);
        return clone;
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

    private static Set<ConversationalDagNode> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
