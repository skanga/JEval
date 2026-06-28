package dev.jeval.metrics;

import dev.jeval.metrics.ToolUseSchemas.ArgumentCorrectnessScore;
import dev.jeval.metrics.ToolUseSchemas.ToolSelectionScore;
import dev.jeval.metrics.ToolUseSchemas.UserInputAndTools;
import java.util.List;

final class ToolUsePrompts {
    private ToolUsePrompts() {
    }

    static String toolSelectionScore(UserInputAndTools data) {
        return """
                You are an expert evaluator assessing the **Tool Selection Quality** of an AI agent.

                OBJECTIVE
                Evaluate whether the agent **selected the most appropriate tools** for completing the user's task, given a list of available tools.

                This metric focuses **only** on which tools were chosen -- not how they were used or whether they succeeded.

                EVALUATION RULES
                1. Relevance: Each tool used must directly support the user's stated goal or a clear sub-task derived from it.
                2. Appropriateness: The chosen tools must match their described purpose.
                3. Necessity: Every tool call must be justified by clear need.
                4. Strictness: When uncertain if a tool was required or correctly chosen, assume it was not appropriate.

                SCORING GUIDE:
                - 1.0: Every tool used was necessary and perfectly matched to the task; no better alternative ignored.
                - 0.75: Tool selection was mostly correct, with only minor redundancy or a small omission.
                - 0.5: Mixed quality; some appropriate selections, but others questionable or missing.
                - 0.25: Poor selection; major mismatches or misuse of available tools.
                - 0.0: Tool selection irrelevant, random, or unjustified.

                OUTPUT FORMAT:
                Return a JSON object with:

                {
                  "score": float between 0.0 and 1.0,
                  "reason": "1-3 factual sentences explaining which tools were appropriate or inappropriate for the task, referencing specific tool names."
                }

                USER INPUT:
                %s

                ASSISTANT MESSAGES:
                %s

                TOOLS CALLED:
                %s

                AVAILABLE TOOLS:
                %s

                JSON:
                """.formatted(data.userMessages(), data.assistantMessages(), data.toolsCalled(), data.availableTools());
    }

    static String argumentCorrectnessScore(UserInputAndTools data) {
        return """
                You are an expert evaluator assessing the **Tool Argument Quality** of an AI agent.

                OBJECTIVE:
                Evaluate whether the **arguments and parameters** passed to each tool were correctly structured, complete, contextually appropriate, and compatible with each tool's intended purpose.

                This metric focuses **only** on argument-level correctness and relevance -- not which tools were chosen.

                EVALUATION RULES
                1. Relevance: Each argument must align with the task and the tool's documented input fields.
                2. Completeness: All required parameters must be provided.
                3. Specificity: Arguments should reflect task-specific values, not generic placeholders.
                4. Justification: Each argument must make sense in context.
                5. Strict Bias: When uncertain whether arguments fit the tool or task, assume they were incorrect.

                SCORING GUIDE:
                - 1.0: All arguments are accurate, specific, and fully aligned with both the task and tool requirements.
                - 0.75: Mostly correct; minor omissions or small mismatches.
                - 0.5: Partial correctness; some valid parameters, but key ones missing or off-target.
                - 0.25: Poor argument quality; several invalid or irrelevant fields.
                - 0.0: Arguments nonsensical, generic, or unrelated to task/tool intent.

                OUTPUT FORMAT:
                Return a JSON object with:

                {
                  "score": float between 0.0 and 1.0,
                  "reason": "1-3 sentences explaining argument alignment or issues, referencing specific parameter names or values when possible."
                }

                USER INPUT:
                %s

                ASSISTANT MESSAGES:
                %s

                TOOLS CALLED:
                %s

                AVAILABLE TOOLS:
                %s

                JSON:
                """.formatted(data.userMessages(), data.assistantMessages(), data.toolsCalled(), data.availableTools());
    }

    static String toolSelectionReason(List<ToolSelectionScore> scores, double finalScore, double threshold) {
        return """
                You are an expert evaluator summarizing the outcome of a **Tool Selection** evaluation.

                Write a single concise explanation that captures why the agent passed or failed based on tool choice quality.
                Focus on which tools were selected and why that selection pattern was or was not appropriate.

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <score> because <your_reason>."
                }

                All Tool Selection Sub-Scores and Reasons:
                %s

                Final Score: %s
                Threshold: %s
                Result: %s

                JSON:
                """.formatted(scores, finalScore, threshold, finalScore >= threshold ? "PASS" : "FAIL");
    }

    static String argumentCorrectnessReason(List<ArgumentCorrectnessScore> scores, double finalScore, double threshold) {
        return """
                You are an expert evaluator summarizing the outcome of a **Tool Argument Quality** evaluation.

                Write a single concise explanation that clearly states why the agent passed or failed in its use of tool arguments.
                Focus strictly on argument correctness and context alignment, not which tools were chosen.

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <score> because <your_reason>."
                }

                All Tool Argument Sub-Scores and Reasons:
                %s

                Final Score: %s
                Threshold: %s
                Result: %s

                JSON:
                """.formatted(scores, finalScore, threshold, finalScore >= threshold ? "PASS" : "FAIL");
    }
}
