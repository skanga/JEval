package dev.jeval.metrics;

import java.util.List;
import java.util.Map;

final class PlanAdherencePrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when judging plan adherence.
            """;

    private PlanAdherencePrompts() {
    }

    static String evaluateAdherence(String task, List<String> plan, Map<String, Object> trace) {
        return """
                You are an **adversarial plan adherence evaluator**. Your goal is to assign the **lowest justifiable score** based on how strictly the agent's actions in the execution trace align with its declared plan.

                INPUTS:
                - **User Task:** The original request or objective.
                - **Agent Plan:** The explicit step-by-step plan the agent was supposed to follow.
                - **Execution Trace:** A detailed record of all agent actions, reasoning, tool calls, and outputs.

                EVALUATION OBJECTIVE:
                Determine whether the agent **exactly and exclusively** followed its plan.
                You are not evaluating success, correctness, or usefulness, only plan obedience.
                Assume non-adherence by default unless clear, direct evidence in the trace proves that each planned step was executed as written and no additional actions occurred.

                STRICT ADHERENCE RULES:
                1. Step Verification: every step in the plan must correspond to a verifiable, explicit action or reasoning entry in the trace.
                Each step must appear in the same logical order as the plan. If a step is missing, only implied, or ambiguous, treat it as not followed.
                2. No Extraneous Actions: any major action, tool call, or reasoning segment not clearly present in the plan should lower the score sharply.
                Extra or unnecessary steps are considered serious violations.
                3. Order Consistency: steps performed in a different order are severe deviations.
                4. Completeness: if even one planned step is missing, skipped, or only partially reflected in the trace, assign the lowest possible score.
                5. Ambiguity Handling: if it is unclear whether a trace action corresponds to a plan step, treat that step as not executed. When uncertain, assign the lower score.
                6. Focus Exclusively on Plan Compliance: ignore task success, reasoning quality, or correctness of outcomes.
                %s

                SCORING SCALE:
                - 1.0: Perfect adherence.
                - 0.75: Strong adherence.
                - 0.5: Partial adherence.
                This should be the highest score possible when there are any deviations.
                - 0.25: Weak adherence.
                - 0.0: No adherence.

                Always err toward the lower score when evidence is partial, ambiguous, or contradictory.

                OUTPUT FORMAT:
                Return a JSON object with exactly this structure:

                {
                  "score": 0.0,
                  "reason": "1-3 concise, factual sentences citing specific matched, missing, or extra steps."
                }

                Requirements for "reason":
                - Reference specific plan step numbers or phrases.
                - Mention concrete trace evidence of mismatches or additions.
                - Avoid subjective adjectives.
                - Be precise and neutral.

                User Task:
                %s

                Agent Plan:
                %s

                Execution Trace:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task, String.join("\n", plan), StepEfficiencyPrompts.toJson(trace));
    }
}
