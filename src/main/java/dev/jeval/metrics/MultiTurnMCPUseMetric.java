package dev.jeval.metrics;

import dev.jeval.ConversationalMetric;
import dev.jeval.ConversationalTestCase;
import dev.jeval.EvaluationModel;
import dev.jeval.MetricResult;
import dev.jeval.MissingTestCaseParamsException;
import dev.jeval.Turn;
import dev.jeval.metrics.MCPTaskCompletionMetric.Task;
import dev.jeval.metrics.MCPUseSchemas.MCPArgsScore;
import dev.jeval.metrics.MCPUseSchemas.MCPPrimitivesScore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MultiTurnMCPUseMetric implements ConversationalMetric {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private final EvaluationModel model;
    private final boolean includeReason;
    private final boolean strictMode;
    private final double threshold;
    private List<Task> tasks = List.of();
    private final List<MCPPrimitivesScore> primitivesScores = new ArrayList<>();
    private final List<MCPArgsScore> argsScores = new ArrayList<>();
    private double score;
    private String reason;
    private boolean success;

    public MultiTurnMCPUseMetric() {
        this(null, 0.5, true, false);
    }

    public MultiTurnMCPUseMetric(EvaluationModel model) {
        this(model, 0.5, true, false);
    }

    public MultiTurnMCPUseMetric(double threshold, boolean includeReason, boolean strictMode) {
        this(null, threshold, includeReason, strictMode);
    }

    public MultiTurnMCPUseMetric(EvaluationModel model, double threshold, boolean includeReason, boolean strictMode) {
        if (!Double.isFinite(threshold)) {
            throw new IllegalArgumentException("Multi-Turn MCP Use threshold must be finite");
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
                    "'mcp_servers' in a conversational test case cannot be empty for the 'MultiTurnMCPUseMetric' metric.");
        }
        tasks = extractTasks(MetricUtils.getUnitInteractions(testCase.turns()));
        primitivesScores.clear();
        argsScores.clear();
        for (var task : tasks) {
            primitivesScores.add(getPrimitivesUsedScore(task, testCase));
            argsScores.add(getArgumentCorrectnessScore(task, testCase));
        }
        score = calculateScore();
        success = score >= threshold;
        reason = includeReason ? generateReason() : null;
        return new MetricResult(name(), score, threshold, success, reason);
    }

    public String name() {
        return "Multi-Turn MCP Use";
    }

    public List<Task> tasks() {
        return tasks;
    }

    public List<MCPPrimitivesScore> primitivesScores() {
        return List.copyOf(primitivesScores);
    }

    public List<MCPArgsScore> argsScores() {
        return List.copyOf(argsScores);
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

    protected MCPPrimitivesScore getPrimitivesUsedScore(Task task, ConversationalTestCase testCase) {
        requireModel();
        var availableTools = MCPUseMetric.formatAvailableMcpTools(testCase.mcpServers());
        return MCPUseSchemas.parsePrimitivesScore(model.generate("""
                Evaluate whether the tools, resources, and prompts used by the agent were appropriate and optimal, based strictly on the list of available tools and resources provided.
                Your job is to determine whether the agent selected the most suitable tools and prompts for the task at hand.
                Output a JSON object with exactly two fields: 'score' and 'reason'.
                %s

                Scoring:
                - 'score' is a float between 0 and 1 inclusive.
                - Use intermediate values (e.g., 0.25, 0.5, 0.75) to reflect partially appropriate tool use, suboptimal decisions, or missed better alternatives.
                - 'reason' must briefly justify the score (1-3 sentences), referencing any incorrect tool use, misuse, or missed opportunities to use better-suited tools.

                CHAIN OF THOUGHT:
                1. Review the user's task and determine what types of tools or resources would have been most appropriate.
                2. Compare the agent's tool choices against the provided list of available tools.
                3. Verify whether any better-suited tools or resources were omitted.
                4. Check for any misuse or unnecessary use of tools or resources.
                5. Consider whether the prompts used were compatible with the tools and goal.

                Return only a valid JSON object. Do not include any explanation or text outside the JSON.

                User Task:
                %s

                Available Tools:
                %s

                Agent Steps:
                %s

                Example Output:
                {
                  "score": 0.75,
                  "reason": "The agent used a tool that was generally appropriate but missed a more specialized tool available in the list that could have provided more accurate results."
                }

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task.task(), availableTools, String.join("\n\n", task.stepsTaken()))));
    }

    protected MCPArgsScore getArgumentCorrectnessScore(Task task, ConversationalTestCase testCase) {
        requireModel();
        var inputSchemas = MCPUseMetric.formatAvailableMcpInputSchemas(testCase.mcpServers());
        return MCPUseSchemas.parseArgsScore(model.generate("""
                Evaluate whether the arguments (inputs) provided by the agent to the tools, resources, and prompts were correct and aligned with their respective input schemas.
                Your job is to determine if the agent supplied appropriate, complete, and well-formatted arguments for each invocation.
                Output a JSON object with exactly two fields: 'score' and 'reason'.
                %s

                Scoring:
                - 'score' is a float between 0 and 1 inclusive.
                - Use intermediate values (e.g., 0.25, 0.5, 0.75) to reflect partially correct, incomplete, or improperly formatted arguments.
                - 'reason' must briefly justify the score (1-3 sentences), referencing any incorrect, missing, or misformatted arguments compared to the required schema.

                CHAIN OF THOUGHT:
                1. Review each step where a tool, resource, or prompt was called.
                2. Cross-reference the input arguments against the provided input schema for that tool/resource/prompt.
                3. Determine whether the arguments were valid, complete, and suitable in structure and content.
                4. Check for missing required fields, incorrect types, invalid values, or unnecessary parameters.
                5. Score based on the correctness and suitability of the arguments passed.

                Return only a valid JSON object. Do not include any explanation or text outside the JSON.

                User Task:
                %s

                Input Schemas:
                %s

                Agent Steps:
                %s

                Example Output:
                {
                  "score": 0.5,
                  "reason": "The agent provided mostly valid fields, but omitted a required parameter and used a string where a list was expected."
                }

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task.task(), inputSchemas, String.join("\n\n", task.stepsTaken()))));
    }

    protected String generateReason() {
        requireModel();
        var reasons = new ArrayList<String>();
        primitivesScores.stream().map(MCPPrimitivesScore::reason).forEach(reasons::add);
        argsScores.stream().map(MCPArgsScore::reason).forEach(reasons::add);
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

    private double calculateScore() {
        var primitiveScore = averagePrimitives();
        var argsScore = averageArgs();
        var rawScore = Math.min(primitiveScore, argsScore);
        return strictMode && rawScore < threshold ? 0.0 : rawScore;
    }

    private double averagePrimitives() {
        var divisor = primitivesScores.isEmpty() ? 1 : primitivesScores.size();
        return primitivesScores.stream().mapToDouble(MCPPrimitivesScore::score).sum() / divisor;
    }

    private double averageArgs() {
        var divisor = argsScores.isEmpty() ? 1 : argsScores.size();
        return argsScores.stream().mapToDouble(MCPArgsScore::score).sum() / divisor;
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
                task.append(turn.content()).append('\n');
            }
            var steps = new ArrayList<String>();
            for (var turn : interaction.subList(1, interaction.size())) {
                steps.add(hasMcpInteraction(turn)
                        ? formatMcpInteraction(turn)
                        : "Agent's response to user: \n" + turn.content());
            }
            extracted.add(new Task(task.toString(), steps));
        }
        return List.copyOf(extracted);
    }

    private static boolean hasMcpInteraction(Turn turn) {
        return turn.mcpInteraction();
    }

    private static String formatMcpInteraction(Turn turn) {
        return MCPTaskCompletionMetric.formatMcpInteraction(turn);
    }

    private void requireModel() {
        if (model == null) {
            throw new UnsupportedOperationException("Multi-Turn MCP Use generation requires a model provider");
        }
    }
}
