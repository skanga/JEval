package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.Turn;
import dev.jeval.metrics.TaskCompletionSchemas.TaskCompletionVerdict;
import java.util.ArrayList;
import java.util.List;

public class MCPTaskCompletionMetric implements ConversationalMetric {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<Task> tasks = List.of();
    private final List<TaskCompletionVerdict> taskScores = new ArrayList<>();
    private double score;
    private String reason;
    private boolean success;

    public MCPTaskCompletionMetric() {
        this(null, 0.5, true, false);
    }

    public MCPTaskCompletionMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public MCPTaskCompletionMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public MCPTaskCompletionMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("MCP Task Completion threshold must be finite");
        }
        this.model = model;
        this.includeReason = includeReason;
        this.strictMode = strictMode;
        this.threshold = strictMode ? 1.0 : threshold;
    }

    @Override
    public MetricResult measure(ConversationalTestCase testCase) {
        if (testCase.mcpServers() == null || testCase.mcpServers().isEmpty()) {
            throw new MissingTestCaseParamsException(
                    "'mcp_servers' in a conversational test case cannot be empty for the 'MCPTaskCompletionMetric' metric.");
        }
        tasks = extractTasks(MetricUtils.getUnitInteractions(testCase.turns()));
        taskScores.clear();
        for (var task : tasks) {
            taskScores.add(generateTaskScore(task));
        }
        score = calculateScore(taskScores);
        success = score >= threshold;
        reason = includeReason ? generateReason(taskScores) : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "MCP Task Completion";
    }

    public List<Task> tasks() {
        return tasks;
    }

    public List<TaskCompletionVerdict> taskScores() {
        return List.copyOf(taskScores);
    }

    public double score() {
        return score;
    }

    public String reason() {
        return reason;
    }

    public boolean success() {
        return success;
    }

    protected TaskCompletionVerdict generateTaskScore(Task task) {
        if (model == null) {
            throw new UnsupportedOperationException("MCP Task Completion generation requires a model provider");
        }
        return TaskCompletionSchemas.parseTaskScore(model.generate("""
                Evaluate whether the user's task has been successfully completed by the agent, based strictly on what the user can see in the agent's responses.
                You must return a JSON object with exactly two fields: 'score' and 'reason'.
                %s

                Scoring:
                - 'score' is a float between 0 and 1 inclusive.
                - Use intermediate values (e.g., 0.25, 0.5, 0.75) to reflect partial task success or missing/inaccurate information.
                - 'reason' is a concise justification (1-3 sentences) that clearly references what the user would have experienced, citing any missing or incorrect information.

                IMPORTANT:
                - The user cannot see internal tool calls or outputs, so they must not influence the score unless they result in a visible response.
                - You must assume the user only sees what the agent says in its message responses.

                CHAIN OF THOUGHT:
                1. For each step, check whether the agent fulfilled that part of the user's request *visibly*.
                2. Confirm that any claims made by the agent are *actually supported* by what was displayed.
                3. Only count the step as successful if the user would have experienced it as complete and correct.

                You must return only a valid JSON object. Do not include any explanation or text outside the JSON.

                User Task:
                %s

                Agent Steps:
                %s

                Example Output:
                {
                  "score": 1.0,
                  "reason": "The agent successfully completed all required steps with accurate results."
                }

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task.task(), String.join("\n\n", task.stepsTaken()))));
    }

    private String generateReason(List<TaskCompletionVerdict> scores) {
        var reasons = scores.stream()
                .map(TaskCompletionVerdict::reason)
                .filter(reason -> reason != null && !reason.isBlank())
                .toList();
        if (model == null) {
            return String.join("\n", reasons);
        }
        return ToolUseSchemas.parseReason(model.generate("""
                You are an AI evaluator producing a single final explanation for an MCP application's evaluation results using the provided reasons.

                Context:
                The reasons are from metrics that were used to evaluate an MCP application by determining whether the model accurately completed a task or called tools and resources with the right arguments.

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <score> because <your_reason>."
                }

                Inputs:
                - final_score: the averaged score across all interactions.
                - success: whether the metric passed or failed
                - reasons: a list of textual reasons generated from individual interactions.

                Instructions:
                1. Read all reasons and synthesize them into one unified explanation.
                2. Do not repeat every reason; merge them into a concise, coherent narrative.
                3. If the metric failed, state the dominant failure reasons. If it passed, state why the application has passed.
                4. Output a single paragraph with no lists, no bullets, no markup.

                Final Score: %s

                Reasons:
                %s

                Success: %s

                Now give me a final reason that explains why the metric passed or failed. Output ONLY the reason and nothing else.

                JSON:
                """.formatted(score, reasons, success))).reason();
    }

    private double calculateScore(List<TaskCompletionVerdict> scores) {
        var divisor = scores.isEmpty() ? 1 : scores.size();
        var total = 0.0;
        for (var taskScore : scores) {
            total += taskScore.verdict();
        }
        var average = total / divisor;
        return strictMode && average < threshold ? 0.0 : average;
    }

    private static List<Task> extractTasks(List<List<Turn>> unitInteractions) {
        var extracted = new ArrayList<Task>();
        for (var interaction : unitInteractions) {
            if (interaction.size() <= 2) {
                continue;
            }
            var task = new StringBuilder();
            for (var turn : interaction) {
                if (!"user".equals(turn.role())) {
                    break;
                }
                task.append(turn.content()).append("\n");
            }
            var steps = new ArrayList<String>();
            for (var i = 1; i < interaction.size(); i++) {
                var turn = interaction.get(i);
                if (hasMcpInteraction(turn)) {
                    steps.add(formatMcpInteraction(turn));
                } else {
                    steps.add("Agent's response to user: \n" + turn.content());
                }
            }
            extracted.add(new Task(task.toString(), steps));
        }
        return List.copyOf(extracted);
    }

    private static boolean hasMcpInteraction(Turn turn) {
        return turn.mcpInteraction();
    }

    static String formatMcpInteraction(Turn turn) {
        var interaction = new StringBuilder("Tools called by agent: \n");
        appendCalls(interaction, "Tool Called", turn.mcpToolsCalled());
        appendCalls(interaction, "Resource Called", turn.mcpResourcesCalled());
        appendCalls(interaction, "Prompt Called", turn.mcpPromptsCalled());
        return interaction.toString();
    }

    private static void appendCalls(StringBuilder builder, String label, List<java.util.Map<String, Object>> calls) {
        if (calls == null) {
            return;
        }
        for (var call : calls) {
            builder.append("\n<").append(label).append(">\n")
                    .append("\n**This does not appear to user**\n")
                    .append(formatCall(label, call))
                    .append("\n</").append(label).append(">\n");
        }
    }

    private static String formatCall(String label, java.util.Map<String, Object> call) {
        return switch (label) {
            case "Tool Called" -> "Name: " + call.get("name")
                    + "\nArgs: " + call.getOrDefault("args", call.get("arguments"))
                    + "\nResult: \n" + toolResult(call.get("result"));
            case "Resource Called" -> "URI: " + call.get("uri")
                    + "\nResult: " + call.get("result");
            case "Prompt Called" -> "Name: " + call.get("name")
                    + "\nResult: " + call.get("result");
            default -> call.toString();
        };
    }

    private static Object toolResult(Object result) {
        if (result instanceof java.util.Map<?, ?> map
                && map.get("structuredContent") instanceof java.util.Map<?, ?> structuredContent
                && structuredContent.containsKey("result")) {
            return structuredContent.get("result");
        }
        return result;
    }

    public record Task(String task, List<String> stepsTaken) {
        public Task {
            stepsTaken = stepsTaken == null ? List.of() : List.copyOf(stepsTaken);
        }
    }
}
