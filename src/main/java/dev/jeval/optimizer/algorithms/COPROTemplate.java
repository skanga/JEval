package dev.jeval.optimizer.algorithms;

public final class COPROTemplate {
    private COPROTemplate() {
    }

    public static String generateBootstrapGuidelines(String originalPrompt, int breadth) {
        return """
                You are an expert prompt engineer. I need to generate %d distinct, high-quality variations of the following prompt.

                [ORIGINAL PROMPT]
                %s

                [INSTRUCTIONS]
                Brainstorm exactly %d diverse "Variation Guidelines". Each guideline should be a 1-2 sentence strategy on how to significantly alter or improve the prompt (e.g., changing the tone, adding reasoning steps, enforcing specific output formats, reordering instructions).
                Make sure the guidelines are completely distinct from one another to ensure a wide search space.

                **
                IMPORTANT: You must only return in JSON format matching the schema.
                Example JSON:
                {
                    "guidelines": [
                        "Reframe the prompt to require step-by-step chain of thought before providing the final answer.",
                        "Condense the instructions into a highly aggressive, concise format avoiding any pleasantries.",
                        "Add strict formatting constraints requiring the output to be bulleted."
                    ]
                }
                **

                JSON:
                """.formatted(breadth, originalPrompt, breadth);
    }

    public static String generateHistoryGuidelines(String originalPrompt, String historyText, int breadth) {
        return """
                You are an expert prompt engineer and diagnostic system. We are using Coordinate Ascent to optimize a prompt.

                [ORIGINAL PROMPT]
                %s

                [PAST ATTEMPTS, SCORES, & EVALUATION FEEDBACK]
                %s

                [INSTRUCTIONS]
                Analyze the [PAST ATTEMPTS, SCORES, & EVALUATION FEEDBACK]. Higher scores are better.
                Crucially, look at the "Evaluation Feedback" for each attempt. This tells you exactly why the prompt lost points (e.g., failed a toxicity metric, missed a formatting constraint).

                Based on this analysis, brainstorm exactly %d new "Variation Guidelines" to try next.
                These guidelines MUST explicitly address and fix the errors mentioned in the evaluation feedback while maintaining the successful traits of the highest-scoring prompts.

                **
                IMPORTANT: You must only return in JSON format matching the schema.
                Example JSON:
                {
                    "guidelines": [
                        "The highest scoring prompts used step-by-step reasoning, but failed the JSON format metric. Add a strict JSON schema constraint.",
                        "Past attempts failed the toxicity metric when being too aggressive. Create a variation that is highly polite but retains the reasoning steps."
                    ]
                }
                **

                JSON:
                """.formatted(originalPrompt, historyText, breadth);
    }

    public static String generateCandidate(String originalPrompt, String guideline, boolean listFormat) {
        var formatInstruction = listFormat
                ? "A JSON array of message objects representing the revised conversational prompt "
                        + "(e.g., [{\"role\": \"system\", \"content\": \"...\"}, "
                        + "{\"role\": \"user\", \"content\": \"...\"}])."
                : "The final string representing the optimized revised prompt.";
        var exampleInstruction = listFormat
                ? "[\n        {\"role\": \"system\", \"content\": \"You are a helpful assistant.\"},\n"
                        + "        {\"role\": \"user\", \"content\": \"{{input}}\"}\n    ]"
                : "\"You are a helpful assistant. Please answer: {{input}}\"";

        return """
                You are an expert prompt engineer. Your task is to rewrite a prompt based strictly on a specific optimization guideline.

                [ORIGINAL PROMPT]
                %s

                [OPTIMIZATION GUIDELINE]
                %s

                [INSTRUCTIONS]
                Rewrite the [ORIGINAL PROMPT] applying the [OPTIMIZATION GUIDELINE].
                1. The new prompt must fulfill the core task of the original prompt.
                2. DO NOT wrap your revised_prompt in markdown blocks (like ```).
                3. If the original prompt uses variable placeholders (like {{input}}), you MUST retain them.

                **
                IMPORTANT: You must only return in JSON format matching the schema.
                "revised_prompt" format: %s

                Example JSON:
                {
                    "thought_process": "The guideline asks to make the prompt more concise. I will remove the introductory pleasantries and state the objective directly.",
                    "revised_prompt": %s
                }
                **

                JSON:
                """.formatted(originalPrompt, guideline, formatInstruction, exampleInstruction);
    }
}
