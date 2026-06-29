package dev.jeval.optimizer.rewriter;

public final class RewriterTemplate {
    private RewriterTemplate() {
    }

    public static String generateMutation(
            String originalPrompt,
            String failures,
            String successes,
            String results,
            String analysis,
            boolean listFormat) {
        var formatInstruction = listFormat
                ? "A JSON array of message objects representing the revised conversational prompt "
                        + "(for example, [{'role': 'system', 'content': '...'}, {'role': 'user', 'content': '...'}])."
                : "The final string representing the optimized revised prompt.";
        var examplePrompt = listFormat
                ? "[{\"role\":\"system\",\"content\":\"You are a helpful assistant...\"},{\"role\":\"user\",\"content\":\"{{input}}\"}]"
                : "\"<the optimized revised prompt here>\"";
        return """
                You are an expert AI Prompt Engineer. Your goal is to perform a 'Prompt Mutation' to move the prompt closer to the Pareto Frontier.

                # Context
                - **Original Prompt:** The current best-performing candidate.
                - **Diagnostic Report:** A 'gradient' signal identifying high-loss areas (low scores) and anchors (high scores).
                - **Failure Cases:** The failure cases from the diagnostic report.
                - **Success Cases:** The success cases from the diagnostic report.
                - **Actual Results:** The actual results from the previous generation.
                - **Overall Analysis:** The overall analysis of the diagnostic report.

                # Original Prompt
                %s

                # Diagnostic Report
                Failures: %s
                Successes: %s

                Actual results from the previous generation: %s

                Overall analysis of the diagnostic report: %s

                # Mutation Instructions
                1. **Targeted Fixes:** Use the Diagnostic Report to apply surgical edits. Focus heavily on examples that received low numerical scores.
                2. **Constraint Satisfaction:** Do NOT degrade performance on anchor examples. Your mutation must be a non-dominated improvement.
                3. **Preserve Placeholders:** Maintain all runtime tokens like `{{input}}` or `{{context}}`.
                4. **Iterative Refinement:** If the report mentions a lack of clarity, add explicit rules or negative constraints.
                5. Always keep the interpolation type of the prompt the same as the original prompt.

                **Output Format**
                Return a JSON object:
                - "thought_process": Explain how you are addressing low-score failures while preserving successes.
                - "revised_prompt": %s

                Example JSON:
                {
                    "thought_process": "<your reasoning here>",
                    "revised_prompt": %s
                }

                JSON:
                """.formatted(
                originalPrompt,
                failures,
                successes,
                results,
                analysis,
                formatInstruction,
                examplePrompt);
    }
}
