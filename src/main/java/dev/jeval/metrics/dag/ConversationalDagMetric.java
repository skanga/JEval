package dev.jeval.metrics.dag;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MultiTurnParam;
import dev.jeval.Turn;
import dev.jeval.metrics.MetricUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConversationalDagMetric implements ConversationalMetric {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private final String name;
    private final ConversationalDeepAcyclicGraph dag;
    private final double threshold;
    private final boolean strictMode;
    private final boolean includeDagSuffix;
    private final boolean includeReason;
    private final Set<MultiTurnParam> requiredParams;
    private final EvaluationModel model;

    public ConversationalDagMetric(String name, ConversationalDeepAcyclicGraph dag) {
        this(name, dag, null, 0.5, false, true, false);
    }

    public ConversationalDagMetric(String name, ConversationalDeepAcyclicGraph dag, EvaluationModel model) {
        this(name, dag, model, 0.5, false, true, false);
    }

    public ConversationalDagMetric(
            String name,
            ConversationalDeepAcyclicGraph dag,
            double threshold,
            boolean strictMode,
            boolean includeDagSuffix) {
        this(name, dag, null, threshold, strictMode, includeDagSuffix, false);
    }

    public ConversationalDagMetric(
            String name,
            ConversationalDeepAcyclicGraph dag,
            EvaluationModel model,
            double threshold,
            boolean strictMode,
            boolean includeDagSuffix) {
        this(name, dag, model, threshold, strictMode, includeDagSuffix, false);
    }

    public ConversationalDagMetric(
            String name,
            ConversationalDeepAcyclicGraph dag,
            EvaluationModel model,
            double threshold,
            boolean strictMode,
            boolean includeDagSuffix,
            boolean includeReason) {
        if (!ConversationalDagUtils.isValidDagFromRoots(dag.rootNodes())) {
            throw new IllegalArgumentException("Cycle detected in DAG graph.");
        }
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Conversational DAG threshold must be finite");
        }
        this.name = name;
        this.dag = ConversationalDagUtils.copyGraph(dag);
        this.threshold = strictMode ? 1.0 : threshold;
        this.strictMode = strictMode;
        this.includeDagSuffix = includeDagSuffix;
        this.includeReason = includeReason;
        this.requiredParams = ConversationalDagUtils.extractRequiredParams(this.dag.rootNodes());
        this.model = model;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        MetricUtils.checkConversationalTestCaseParams(testCase, new ArrayList<>(requiredParams), name());
        ConversationalDagUtils.validateTurnWindows(dag.rootNodes(), testCase.turns());
        var state = new ExecutionState(new IdentityHashMap<>(), new IdentityHashMap<>());
        NodeMeasurement measurement = null;
        for (var rootNode : dag.rootNodes()) {
            var rootMeasurement = measureNode(rootNode, testCase, state);
            if (rootMeasurement != null) {
                measurement = rootMeasurement;
            }
        }
        return new MetricResult(
                name(), measurement.score(), threshold, measurement.score() >= threshold, measurement.reason());
    }

    private NodeMeasurement measureNode(
            ConversationalDagNode node,
            ConversationalTestCase testCase,
            ExecutionState state) {
        if (!isReady(node, state)) {
            return null;
        }
        if (node instanceof ConversationalVerdictNode verdict) {
            if (verdict.score() != null) {
                var score = verdict.score() / 10.0;
                return new NodeMeasurement(score, generateReason(score));
            }
            if (verdict.metric() != null) {
                var result = verdict.metric().measure(testCase);
                return new NodeMeasurement(result.score(), includeReason ? result.reason() : null);
            }
            return measureNode(verdict.child(), testCase, state);
        }
        requireModel();
        if (node instanceof ConversationalTaskNode task) {
            if (task.evaluationParams().isEmpty() && task.parents().isEmpty()) {
                throw new IllegalArgumentException(
                        "ConversationalTaskNode must have either evaluationParams or parent node(s).");
            }
            var output = DagSchemas.parseTaskNodeOutput(
                    model.generate(taskPrompt(task, testCase, state.taskOutputs()))).output();
            state.taskOutputs().put(task, output);
            if (task.children().isEmpty()) {
                throw new IllegalArgumentException("ConversationalTaskNode must have at least one child to execute.");
            }
            return task.children().stream()
                    .map(child -> measureNode(child, testCase, state))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(NodeMeasurement::score))
                    .orElse(null);
        }
        if (node instanceof ConversationalBinaryJudgementNode judgement) {
            var verdict = DagSchemas.parseBinaryJudgementVerdict(
                    model.generate(judgementPrompt(judgement, testCase, state.taskOutputs())));
            return judgement.children().stream()
                    .filter(child -> Boolean.valueOf(verdict.verdict()).equals(child.verdict()))
                    .findFirst()
                    .map(child -> measureNode(child, testCase, state))
                    .orElseThrow(() -> unmatchedVerdict(verdict.verdict()));
        }
        if (node instanceof ConversationalNonBinaryJudgementNode judgement) {
            var verdict = DagSchemas.parseNonBinaryJudgementVerdict(
                    model.generate(judgementPrompt(judgement, testCase, state.taskOutputs())));
            return judgement.children().stream()
                    .filter(child -> verdict.verdict().equals(child.verdict()))
                    .findFirst()
                    .map(child -> measureNode(child, testCase, state))
                    .orElseThrow(() -> unmatchedVerdict(verdict.verdict()));
        }
        throw new IllegalArgumentException("Unsupported conversational DAG node type: " + node.getClass().getSimpleName());
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Conversational DAG metric generation requires a model provider");
        }
    }

    private static boolean isReady(ConversationalDagNode node, ExecutionState state) {
        var remaining = state.remainingIndegrees().compute(node, (ignored, current) ->
                current == null ? node.indegree() - 1 : current - 1);
        return remaining <= 0;
    }

    private String generateReason(double score) {
        if (!includeReason || model == null) {
            return null;
        }
        return DagSchemas.parseMetricScoreReason(model.generate(reasonPrompt(score, name()))).reason();
    }

    private static String reasonPrompt(double score, String name) {
        return "Given the metric " + name + " score " + score + ", return JSON with a reason field.";
    }

    private static IllegalArgumentException unmatchedVerdict(Object verdict) {
        return new IllegalArgumentException("No conversational DAG verdict child matched model verdict: " + verdict);
    }

    private static String taskPrompt(
            ConversationalTaskNode task,
            ConversationalTestCase testCase,
            Map<ConversationalTaskNode, Object> taskOutputs) {
        return """
                %s
                Instructions:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                task.instructions(),
                textFor(task, task.evaluationParams(), task.turnWindow(), testCase, taskOutputs));
    }

    private static String judgementPrompt(
            ConversationalBinaryJudgementNode judgement,
            ConversationalTestCase testCase,
            Map<ConversationalTaskNode, Object> taskOutputs) {
        return """
                %s
                Criteria:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                judgement.criteria(),
                textFor(judgement, judgement.evaluationParams(), judgement.turnWindow(), testCase, taskOutputs));
    }

    private static String judgementPrompt(
            ConversationalNonBinaryJudgementNode judgement,
            ConversationalTestCase testCase,
            Map<ConversationalTaskNode, Object> taskOutputs) {
        return """
                %s
                Criteria:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                judgement.criteria(),
                textFor(judgement, judgement.evaluationParams(), judgement.turnWindow(), testCase, taskOutputs));
    }

    private static String textFor(
            ConversationalDagNode node,
            List<MultiTurnParam> params,
            TurnWindow turnWindow,
            ConversationalTestCase testCase,
            Map<ConversationalTaskNode, Object> taskOutputs) {
        var text = new StringBuilder();
        for (var parent : node.parents()) {
            if (parent instanceof ConversationalTaskNode task && taskOutputs.containsKey(task)) {
                text.append(task.outputLabel()).append(":\n").append(taskOutputs.get(task)).append("\n\n");
            }
        }
        if (!params.isEmpty()) {
            text.append("Full Conversation: \n");
            var window = turnWindow == null ? new TurnWindow(0, testCase.turns().size() - 1) : turnWindow;
            for (var index = window.start(); index <= window.end(); index++) {
                var turn = testCase.turns().get(index);
                for (var param : params) {
                    text.append(labelFor(param)).append(":\n").append(turnValue(turn, param)).append("\n\n");
                }
            }
        }
        return text.toString();
    }

    private static Object turnValue(Turn turn, MultiTurnParam param) {
        return switch (param) {
            case CONTENT -> turn.content();
            case ROLE -> turn.role();
            case RETRIEVAL_CONTEXT -> turn.retrievalContext();
            case TOOLS_CALLED -> turn.toolsCalled();
            case MCP_TOOLS -> turn.mcpToolsCalled();
            case MCP_RESOURCES -> turn.mcpResourcesCalled();
            case MCP_PROMPTS -> turn.mcpPromptsCalled();
            default -> null;
        };
    }

    private static String labelFor(MultiTurnParam param) {
        return switch (param) {
            case CONTENT -> "Content";
            case ROLE -> "Role";
            case METADATA -> "Metadata";
            case TAGS -> "Tags";
            case TOOLS_CALLED -> "Tools Called";
            case RETRIEVAL_CONTEXT -> "Retrieval Context";
            case EXPECTED_OUTCOME -> "Expected Outcome";
            case SCENARIO -> "Scenario";
            default -> param.value();
        };
    }

    public String name() {
        return includeDagSuffix ? name + " [ConversationalDAG]" : name;
    }

    public ConversationalDeepAcyclicGraph dag() {
        return dag;
    }

    public double threshold() {
        return threshold;
    }

    public boolean strictMode() {
        return strictMode;
    }

    public Set<MultiTurnParam> requiredParams() {
        return requiredParams;
    }

    private record NodeMeasurement(double score, String reason) {
    }

    private record ExecutionState(
            Map<ConversationalTaskNode, Object> taskOutputs,
            Map<ConversationalDagNode, Integer> remainingIndegrees) {
    }
}
