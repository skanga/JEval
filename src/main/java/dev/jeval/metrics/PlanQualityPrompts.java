package dev.jeval.metrics;

import java.util.List;
import java.util.Map;

final class PlanQualityPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when judging plan quality.
            """;

    private PlanQualityPrompts() {
    }

    static String extractPlanFromTrace(Map<String, Object> trace) {
        return """
                You are a **systems analyst** evaluating an AI agent's execution trace.

                Your sole task is to extract the explicit or clearly implied plan the agent followed or intended to follow, only if that plan is directly evidenced in the trace.

                STRICT RULES TO FOLLOW:
                1. Source Evidence Requirement: Every plan step you include must be directly supported by explicit text in the trace.
                2. No Hallucination Policy: do not infer or invent steps. If there is no coherent plan present, output an empty list.
                3. Focus on Intent, Not Outcomes: if the stated plan differs from execution, still extract the intended steps only if traceable.
                4. Granularity: each step should represent a single distinct action or intention.
                5. Neutral Language: reproduce plan steps with neutral, minimal paraphrasing.
                %s

                OUTPUT FORMAT:

                Return a JSON object with exactly this structure:
                {
                  "plan": [
                    "step 1",
                    "step 2"
                  ]
                }

                If no plan is evidenced in the trace, return:
                {
                  "plan": []
                }

                Do not include commentary, confidence scores, or explanations.

                TRACE:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, StepEfficiencyPrompts.toJson(trace));
    }

    static String evaluatePlanQuality(String task, List<String> plan) {
        return """
                You are a **plan quality evaluator**. Your task is to critically assess the **quality, completeness, and optimality** of an AI agent's plan to accomplish the given user task.

                INPUTS:
                - **User Task:** The user's explicit goal or instruction.
                - **Agent Plan:** The ordered list of steps the agent intends to follow to achieve that goal.

                EVALUATION OBJECTIVE:
                Judge the **intrinsic quality** of the plan, whether the plan itself is strong enough to fully and efficiently achieve the user's task.

                The evaluation must be **strict**. If the plan is incomplete, inefficient, redundant, or missing critical details, assign a very low score.

                STRICT EVALUATION CRITERIA:
                1. Completeness: the plan must fully address all major requirements of the user task.
                Missing even one critical subtask or dependency should reduce the score sharply.
                The plan must include all prerequisite actions necessary for the final outcome.
                2. Logical Coherence: steps must follow a clear, rational sequence that leads directly to completing the task.
                Every step must have a clear purpose; no filler or irrelevant actions.
                3. Optimality and Efficiency: the plan must be minimal but sufficient, with no unnecessary or repetitive steps.
                If a more direct, simpler, or logically superior plan could achieve the same outcome, the current plan should receive a lower score.
                4. Level of Detail: each step should be specific enough for an agent to execute it reliably.
                5. Alignment with Task: the plan must explicitly and directly target the user's stated goal.
                %s

                SCORING SCALE (STRICT):
                - 1.0: Excellent plan.
                - 0.75: Good plan.
                - 0.5: Adequate but flawed plan.
                - 0.25: Weak plan.
                - 0.0: Inadequate plan.

                When in doubt, assign the lower score.

                OUTPUT FORMAT:

                Return a JSON object with this exact structure:

                {
                  "score": 0.0,
                  "reason": "1-3 short, precise sentences explaining what the plan lacks or how it could fail."
                }

                The "reason" must:
                - Reference specific missing, unclear, or inefficient steps.
                - Avoid vague language.
                - Use objective terms describing gaps or weaknesses.

                User Task:
                %s

                Agent Plan:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task, String.join("\n", plan));
    }
}
