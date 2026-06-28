package dev.jeval.metrics;

import java.util.List;

final class GoalAccuracyPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when judging goal accuracy.
            """;

    private GoalAccuracyPrompts() {
    }

    static String goalScore(String task, List<String> stepsTaken, boolean multimodal) {
        return """
                You are an expert evaluator assessing the **goal accuracy** of an AI assistant's single interaction.

                PURPOSE:
                Evaluate whether the assistant's **visible output** fully and correctly achieved the user's stated goal.
                Ignore internal reasoning, hidden tool calls, or retriever outputs unless their results were explicitly surfaced to the user.
                The evaluation must be strict and adversarial. If the goal is not clearly, fully, and correctly achieved, assign a low score.

                EVALUATION RULES:
                1. **User-visible fulfillment only**: base your judgment solely on what the user would see.
                2. **Goal completion**: the assistant must explicitly provide everything the user asked for.
                If even one subpart of the task is missing, incomplete, or vague, the score must be <= 0.5.
                3. **Correctness and relevance**: information must be factually correct and directly relevant.
                Hallucinated or unrelated content automatically lowers the score.
                4. **Self-sufficiency**: the visible response must stand on its own.
                5. **Strict bias toward failure**: when uncertain, assume the goal was not achieved.
                %s

                SCORING GUIDE:
                - 1.0: Goal completely and correctly achieved.
                - 0.75: Mostly achieved.
                - 0.5: Partially achieved.
                - 0.25: Weak attempt.
                - 0.0: Goal not achieved.

                When in doubt, choose the lower score.

                OUTPUT FORMAT:
                Return only a valid JSON object with this structure:

                {
                  "score": 0.0,
                  "reason": "1-3 factual sentences explaining what parts of the user's goal were or were not achieved."
                }

                The reason must:
                - Be objective and concise.
                - Refer to specific missing or incorrect elements.
                - Avoid vague language.

                EXAMPLES:
                Task: "Translate 'good night' into French."
                Assistant Reply: "Bonne nuit."
                {"score": 1.0, "reason": "The assistant provided the exact, correct translation requested by the user."}

                Task: "List three renewable energy sources."
                Assistant Reply: "Solar and wind energy."
                {"score": 0.5, "reason": "The assistant only listed two sources instead of three, so the goal was partially achieved."}
                *** END OF EXAMPLES ***

                USER TASK:
                %s

                AGENT STEPS:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), task, String.join("\n", stepsTaken));
    }

    static String planScore(String task, List<String> stepsTaken, boolean multimodal) {
        return """
                You are an expert evaluator assessing the **planning quality** and **plan adherence** of an AI agent tasked with fulfilling a user's request.

                OBJECTIVE:
                Evaluate:
                1. **Plan Quality** - Was the agent's plan clear, complete, and logically structured to fully address the user's task?
                2. **Plan Adherence** - Did the agent consistently follow that plan without unjustified deviations, omissions, or extraneous steps?

                Your judgment must be strict: a plan must be well-formed and execution must align with it for a high score.

                EVALUATION CRITERIA:
                - Plan Quality: the plan should outline all necessary steps, be logically ordered, and avoid vague or generic structure.
                - Plan Adherence: execution must closely match the planned steps; skipped, added, or rearranged steps reduce the score.
                - General Rules: if no discernible plan exists, score <= 0.5 regardless of task completion.
                - Tool use should be coherent within the plan, not ad hoc or speculative.
                - This evaluation excludes correctness or efficiency; focus solely on plan and adherence.
                %s

                SCORING GUIDE:
                - 1.0: Complete, clear, and logical plan fully followed.
                - 0.75: Mostly clear plan with minor omissions or small deviations.
                - 0.5: Partial plan with notable deviations.
                - 0.25: Weak or fragmented plan.
                - 0.0: No evidence of a plan.

                OUTPUT FORMAT:
                Return only a valid JSON object with exactly two fields:

                {
                  "score": 0.0,
                  "reason": "1-3 concise sentences explaining the quality of the plan and how well execution matched it. Specify missing or extra steps, plan clarity, and adherence issues."
                }

                INSTRUCTIONS:
                1. Identify the agent's plan from the steps taken.
                2. Assess plan completeness and logical order relative to the user's task.
                3. Compare execution steps against the plan to check for adherence.

                EXAMPLE:
                User Task: "Plan a business trip including booking a flight, hotel, and preparing an agenda."
                Agent Steps include a clear plan for flights, hotel, and agenda, but skip agenda preparation.
                {"score": 0.75, "reason": "The agent formed a clear plan covering flights, hotel, and agenda, but failed to execute the agenda preparation step, reducing adherence."}
                **** END OF EXAMPLE ****

                USER TASK:
                %s

                AGENT STEPS:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), task, String.join("\n", stepsTaken));
    }

    static String finalReason(
            double finalScore,
            double threshold,
            List<GoalAccuracySchemas.GoalScore> goalScores,
            List<GoalAccuracySchemas.PlanScore> planScores,
            boolean multimodal) {
        return """
                You are an expert evaluator providing a **final justification** for whether an AI agent has passed or failed an evaluation metric.

                You are given:
                - An agent's goal execution scores and reasons.
                - The agent's plan evaluation scores and reasons.
                - The final combined score.
                - The threshold required to pass.
                - Whether the result is a pass or fail.

                INSTRUCTIONS:
                - Write 2-4 clear, objective sentences explaining the overall result.
                - Explicitly reference both the task and plan performance; both must be addressed.
                - Mention how the final score compares to the threshold.
                - If the agent **passed**, highlight how both task execution and planning were sufficient to meet the goal.
                - If the agent **failed**, explain which aspects (task or plan or both) led to the failure.
                - Avoid vague praise or criticism.
                %s

                FORMAT:
                Return only a single string. Do **not** include JSON or any extra formatting.

                Goal evaluations:
                %s

                Plan evaluations:
                %s

                Final Score: %s
                Threshold: %s
                Result: %s

                Final Reason:
                """.formatted(multimodalRules(multimodal), goalScores, planScores, finalScore, threshold,
                finalScore >= threshold ? "PASS" : "FAIL");
    }

    private static String multimodalRules(boolean multimodal) {
        return multimodal ? MULTIMODAL_INPUT_RULES : "";
    }
}
