package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class PromptAlignmentPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            - Treat image content as factual evidence.
            - Only reference visual details that are explicitly and clearly visible.
            - Do not infer or guess objects, text, or details not visibly present.
            - If an image is unclear or ambiguous, mark uncertainty explicitly.
            """;

    private PromptAlignmentPrompts() {
    }

    static String generateVerdicts(List<String> promptInstructions, String input, String actualOutput) {
        return """
                For the provided list of prompt instructions, determine whether each instruction has been followed in the LLM actual output.
                Please generate a list of JSON with two keys: `verdict` and `reason`.
                The 'verdict' key should STRICTLY be either a 'yes' or 'no'. Only answer 'yes' if the instruction COMPLETELY follows the instruction, and 'no' otherwise.
                You should be EXTRA STRICT AND CAREFUL when giving a 'yes'.
                The 'reason' is the reason for the verdict.
                Provide a 'reason' ONLY if the answer is 'no'.
                The provided prompt instructions are the instructions to be followed in the prompt, which you have no access to.

                %s

                IMPORTANT: Please make sure to only return in JSON format, with the 'verdicts' key mapping to a list of JSON objects.
                Example input: What number is the stars of the sky?
                Example actual output: HEY THERE! I think what you meant is "What is the number of stars in the sky", but unfortunately I don't know the answer to it.
                Example prompt instructions: ["Answer the input in a well-mannered fashion.", "Do not correct user of any grammatical errors.", "Respond in all upper case"]
                Example JSON:
                {
                  "verdicts": [
                    {
                      "verdict": "yes"
                    },
                    {
                      "reason": "The LLM corrected the user when the user used the wrong grammar in asking about the number of stars in the sky.",
                      "verdict": "no"
                    },
                    {
                      "reason": "The LLM only made 'HEY THERE' uppercase, which does not follow the instruction of making everything uppercase completely.",
                      "verdict": "no"
                    }
                  ]
                }

                Since you are going to generate a verdict for each instruction, the number of 'verdicts' SHOULD BE STRICTLY EQUAL to the number of prompt instructions.

                Prompt Instructions:
                %s

                Input:
                %s

                LLM Actual Output:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, promptInstructions, input, actualOutput);
    }

    static String generateReason(double score, List<String> unalignmentReasons, String input, String actualOutput) {
        return """
                Given the prompt alignment score, the reasons for unalignment found in the LLM actual output, the actual output, and input, provide a CONCISE reason for the score.
                Explain why it is not higher, but also why it is at its current score.
                The unalignments represent prompt instructions that are not followed by the LLM in the actual output.
                If there are no unalignments, just say something positive with an upbeat encouraging tone, but don't overdo it otherwise it gets annoying.
                Do not talk about whether the actual output is a good fit for the input; assess ENTIRELY based on the unalignment reasons.

                %s

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <prompt_alignment_score> because <your_reason>."
                }

                Input:
                %s

                LLM Actual Output:
                %s

                Prompt Alignment Score:
                %s

                Reasons for unalignment:
                %s

                JSON:
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                input,
                actualOutput,
                String.format(Locale.ROOT, "%.2f", score),
                unalignmentReasons);
    }
}
