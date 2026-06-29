package dev.jeval.optimizer.algorithms;

public final class MIPROV2Template {
    private MIPROV2Template() {
    }

    public static String generateDatasetSummary(String examplesText) {
        return """
                You are an expert AI data analyst. Your task is to analyze a sample of inputs and expected outputs from a specific task and summarize the core objective.

                [EXAMPLE DATA]
                %s

                [INSTRUCTIONS]
                Based on the examples above, write a concise 2-3 sentence summary of the dataset.
                You MUST identify:
                1. The overarching objective (What is the task trying to achieve?)
                2. The expected format (How should the outputs be structured?)
                3. Potential edge cases (Are there trick questions, specific constraints, or exceptions?)

                **
                IMPORTANT: You must only return in JSON format matching the schema.
                Example JSON:
                {
                    "summary": "The objective is to classify sentiment. The format is always a single word ('Positive' or 'Negative'). Edge cases include sarcastic inputs which should be classified based on literal text."
                }
                **

                JSON:
                """.formatted(examplesText);
    }

    public static String generateInstructionProposal(
            String currentPrompt,
            String datasetSummary,
            String examplesText,
            String tip,
            int candidateIndex,
            boolean listFormat) {
        var formatInstruction = listFormat
                ? "A STRICT JSON array of message objects representing the revised conversational prompt "
                        + "(e.g., [{\"role\": \"system\", \"content\": \"...\"}, {\"role\": \"user\", \"content\": \"...\"}])."
                : "The final string representing the optimized revised instruction.";
        var exampleInstruction = listFormat
                ? """
                        [{"role": "system", "content": "Determine the sentiment of the text. Pay special attention to sarcasm..."},{"role": "user", "content": "{{input}}"}]"""
                : """
                        "Determine the sentiment of the text. Respond with only 'Positive' or 'Negative'. Pay special attention to sarcasm...\"""";

        return """
                You are an expert prompt engineer. Your task is to propose an improved instruction for an LLM task.

                [CURRENT PROMPT]
                %s

                [DATASET SUMMARY]
                %s

                [EXAMPLE INPUTS/OUTPUTS]
                %s

                [GENERATION TIP]
                %s

                [INSTRUCTIONS]
                Based on the [CURRENT PROMPT], the global [DATASET SUMMARY], the specific examples, and the [GENERATION TIP], propose an improved version of the prompt.
                This is candidate #%d. You must critically apply the [GENERATION TIP] to make this candidate meaningfully different from trivial variations.

                --- RULES ---
                1. Focus on improving clarity, effectiveness, and alignment with the task requirements.
                2. DO NOT hardcode the specific examples from [EXAMPLE INPUTS/OUTPUTS] directly into your new instruction. The instruction must generalize to all data.
                3. Keep the instruction self-contained and actionable.
                4. DO NOT wrap your revised_instruction in markdown blocks (like ```).
                5. Always keep the interpolation type of the prompt the same as the current prompt. We use regex to interpolate the prompt so keep the same format.
                6. If revised_instruction is LIST format, it MUST be a valid JSON array starting with `[` and ending with `]`.
                7. For LIST format, every element must be an object with exactly: "role" and "content" keys.
                8. Do NOT output multiple top-level JSON objects separated by commas. Output one JSON array only.

                **
                IMPORTANT: You must only return in JSON format matching the schema.
                You must provide your 'thought_process' first, explaining how you will apply the tip and summary, followed by the 'revised_instruction'.

                "revised_instruction": format %s

                Example JSON:
                {
                    "thought_process": "The dataset summary indicates we need to handle sarcastic edge cases. The tip says 'Be concise'. I will update the prompt to explicitly mention sarcasm while removing the wordy introductory sentences.",
                    "revised_instruction": %s
                }
                **

                JSON:
                """.formatted(
                currentPrompt,
                datasetSummary,
                examplesText,
                tip,
                candidateIndex + 1,
                formatInstruction,
                exampleInstruction);
    }
}
