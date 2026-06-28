package dev.jeval.metrics.dag;

import dev.jeval.EvaluationModel;
import dev.jeval.LlmTestCase;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.SingleTurnParam;
import dev.jeval.metrics.MetricUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DagMetric implements Metric {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private final String name;
    private final DeepAcyclicGraph dag;
    private final double threshold;
    private final boolean strictMode;
    private final boolean includeDagSuffix;
    private final boolean includeReason;
    private final Set<SingleTurnParam> requiredParams;
    private final EvaluationModel model;

    public DagMetric(String name, DeepAcyclicGraph dag) {
        this(name, dag, null, 0.5, false, true, false);
    }

    public DagMetric(String name, DeepAcyclicGraph dag, EvaluationModel model) {
        this(name, dag, model, 0.5, false, true, false);
    }

    public DagMetric(String name, DeepAcyclicGraph dag, double threshold, boolean strictMode, boolean includeDagSuffix) {
        this(name, dag, null, threshold, strictMode, includeDagSuffix, false);
    }

    public DagMetric(
            String name,
            DeepAcyclicGraph dag,
            EvaluationModel model,
            double threshold,
            boolean strictMode,
            boolean includeDagSuffix) {
        this(name, dag, model, threshold, strictMode, includeDagSuffix, false);
    }

    public DagMetric(
            String name,
            DeepAcyclicGraph dag,
            EvaluationModel model,
            double threshold,
            boolean strictMode,
            boolean includeDagSuffix,
            boolean includeReason) {
        if (!DagUtils.isValidDagFromRoots(dag.rootNodes())) {
            throw new IllegalArgumentException("Cycle detected in DAG graph.");
        }
        this.name = name;
        this.dag = DagUtils.copyGraph(dag);
        this.threshold = strictMode ? 1.0 : threshold;
        this.strictMode = strictMode;
        this.includeDagSuffix = includeDagSuffix;
        this.includeReason = includeReason;
        this.requiredParams = DagUtils.extractRequiredParams(this.dag.rootNodes());
        this.model = model;
    }

    @Override
    public MetricResult measure(LlmTestCase testCase) {
        MetricUtils.checkLlmTestCaseParams(testCase, new ArrayList<>(requiredParams), name());
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

    private NodeMeasurement measureNode(DagNode node, LlmTestCase testCase, ExecutionState state) {
        if (!isReady(node, state)) {
            return null;
        }
        if (node instanceof VerdictNode verdict) {
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
        if (node instanceof TaskNode task) {
            if (task.evaluationParams().isEmpty() && task.parents().isEmpty()) {
                throw new IllegalArgumentException("TaskNode must have either evaluationParams or parent node(s).");
            }
            var output = DagSchemas.parseTaskNodeOutput(model.generate(taskPrompt(task, testCase, state.taskOutputs()))).output();
            state.taskOutputs().put(task, output);
            if (task.children().isEmpty()) {
                throw new IllegalArgumentException("TaskNode must have at least one child to execute.");
            }
            return task.children().stream()
                    .map(child -> measureNode(child, testCase, state))
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingDouble(NodeMeasurement::score))
                    .orElse(null);
        }
        if (node instanceof BinaryJudgementNode judgement) {
            var verdict = DagSchemas.parseBinaryJudgementVerdict(
                    model.generate(judgementPrompt(judgement, testCase, state.taskOutputs())));
            return judgement.children().stream()
                    .filter(child -> Boolean.valueOf(verdict.verdict()).equals(child.verdict()))
                    .findFirst()
                    .map(child -> measureNode(child, testCase, state))
                    .orElseThrow(() -> unmatchedVerdict(verdict.verdict()));
        }
        if (node instanceof NonBinaryJudgementNode judgement) {
            var verdict = DagSchemas.parseNonBinaryJudgementVerdict(
                    model.generate(judgementPrompt(judgement, testCase, state.taskOutputs())));
            return judgement.children().stream()
                    .filter(child -> verdict.verdict().equals(child.verdict()))
                    .findFirst()
                    .map(child -> measureNode(child, testCase, state))
                    .orElseThrow(() -> unmatchedVerdict(verdict.verdict()));
        }
        throw new IllegalArgumentException("Unsupported DAG node type: " + node.getClass().getSimpleName());
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("DAG metric generation requires a model provider");
        }
    }

    private static boolean isReady(DagNode node, ExecutionState state) {
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
        return new IllegalArgumentException("No DAG verdict child matched model verdict: " + verdict);
    }

    private static String taskPrompt(TaskNode task, LlmTestCase testCase, Map<TaskNode, Object> taskOutputs) {
        return """
                %s
                Instructions:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                task.instructions(),
                textFor(task, task.evaluationParams(), testCase, taskOutputs));
    }

    private static String judgementPrompt(
            BinaryJudgementNode judgement, LlmTestCase testCase, Map<TaskNode, Object> taskOutputs) {
        return """
                %s
                Criteria:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                judgement.criteria(),
                textFor(judgement, judgement.evaluationParams(), testCase, taskOutputs));
    }

    private static String judgementPrompt(
            NonBinaryJudgementNode judgement, LlmTestCase testCase, Map<TaskNode, Object> taskOutputs) {
        return """
                %s
                Criteria:
                %s

                Text:
                %s
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                judgement.criteria(),
                textFor(judgement, judgement.evaluationParams(), testCase, taskOutputs));
    }

    private static String textFor(
            DagNode node,
            Iterable<SingleTurnParam> params,
            LlmTestCase testCase,
            Map<TaskNode, Object> taskOutputs) {
        var text = new StringBuilder();
        for (var parent : node.parents()) {
            if (parent instanceof TaskNode task && taskOutputs.containsKey(task)) {
                text.append(task.outputLabel()).append(":\n").append(taskOutputs.get(task)).append("\n\n");
            }
        }
        for (var param : params) {
            text.append(labelFor(param)).append(":\n").append(value(testCase, param)).append("\n");
        }
        return text.toString();
    }

    private static String labelFor(SingleTurnParam param) {
        return switch (param) {
            case INPUT -> "Input";
            case ACTUAL_OUTPUT -> "Actual Output";
            case EXPECTED_OUTPUT -> "Expected Output";
            case CONTEXT -> "Context";
            case RETRIEVAL_CONTEXT -> "Retrieval Context";
            case METADATA -> "Metadata";
            case TAGS -> "Tags";
            case EXPECTED_TOOLS -> "Expected Tools";
            case TOOLS_CALLED -> "Tools Called";
            default -> param.value();
        };
    }

    private static Object value(LlmTestCase testCase, SingleTurnParam param) {
        return switch (param) {
            case INPUT -> testCase.input();
            case ACTUAL_OUTPUT -> testCase.actualOutput();
            case EXPECTED_OUTPUT -> testCase.expectedOutput();
            case CONTEXT -> testCase.context();
            case RETRIEVAL_CONTEXT -> testCase.retrievalContext();
            case METADATA -> testCase.metadata();
            case TAGS -> testCase.tags();
            case TOOLS_CALLED -> testCase.toolsCalled();
            case EXPECTED_TOOLS -> testCase.expectedTools();
            case MCP_SERVERS -> testCase.mcpServers();
            case MCP_TOOLS_CALLED -> testCase.mcpToolsCalled();
            case MCP_RESOURCES_CALLED -> testCase.mcpResourcesCalled();
            case MCP_PROMPTS_CALLED -> testCase.mcpPromptsCalled();
        };
    }

    public String name() {
        return includeDagSuffix ? name + " [DAG]" : name;
    }

    public DeepAcyclicGraph dag() {
        return dag;
    }

    public double threshold() {
        return threshold;
    }

    public boolean strictMode() {
        return strictMode;
    }

    public Set<SingleTurnParam> requiredParams() {
        return requiredParams;
    }

    private record NodeMeasurement(double score, String reason) {
    }

    private record ExecutionState(Map<TaskNode, Object> taskOutputs, Map<DagNode, Integer> remainingIndegrees) {
    }
}
