package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class AnswerRelevancyPrompts {
    private static final String MULTIMODAL_RULES = """

            --- MULTIMODAL INPUT RULES ---
            - Treat image content as factual evidence.
            - Only reference visual details that are explicitly and clearly visible.
            - Do not infer or guess objects, text, or details not visibly present.
            - If an image is unclear or ambiguous, mark uncertainty explicitly.
            """;

    private AnswerRelevancyPrompts() {}

    static String generateStatements(String actualOutput, boolean multimodal) {
        return """
                Given the text, breakdown and generate a list of statements presented. Ambiguous statements and single words can be considered as statements, but only if outside of a coherent statement.

                Example:
                Example text:
                Our new laptop model features a high-resolution Retina display for crystal-clear visuals. It also includes a fast-charging battery, giving you up to 12 hours of usage on a single charge. For security, we've added fingerprint authentication and an encrypted SSD. Plus, every purchase comes with a one-year warranty and 24/7 customer support.
                %s
                {
                  "statements": [
                    "The new laptop model has a high-resolution Retina display.",
                    "It includes a fast-charging battery with up to 12 hours of usage.",
                    "Security features include fingerprint authentication and an encrypted SSD.",
                    "Every purchase comes with a one-year warranty.",
                    "24/7 customer support is included."
                  ]
                }
                ===== END OF EXAMPLE ======

                **
                IMPORTANT: Please make sure to only return in valid and parseable JSON format, with the "statements" key mapping to a list of strings. No words or explanation are needed. Ensure all strings are closed appropriately. Repair any invalid JSON before you output it.
                **

                Text:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), actualOutput);
    }

    static String generateVerdicts(String input, List<String> statements, boolean multimodal) {
        return """
                For the provided list of statements, determine whether each statement is relevant to address the input.
                Generate JSON objects with 'verdict' and 'reason' fields.
                The 'verdict' should be 'yes' (relevant), 'no' (irrelevant), or 'idk' (ambiguous/supporting information).
                Provide 'reason' ONLY for 'no' or 'idk' verdicts.
                The statements are from an AI's actual output.
                %s
                **
                IMPORTANT: Please make sure to only return in valid and parseable JSON format, with the 'verdicts' key mapping to a list of JSON objects. Ensure all strings are closed appropriately. Repair any invalid JSON before you output it.

                Expected JSON format:
                {
                  "verdicts": [
                    {
                      "verdict": "yes"
                    },
                    {
                      "reason": <explanation_for_irrelevance>,
                      "verdict": "no"
                    },
                    {
                      "reason": <explanation_for_ambiguity>,
                      "verdict": "idk"
                    }
                  ]
                }

                Generate ONE verdict per statement - number of 'verdicts' MUST equal number of statements.
                'verdict' must be STRICTLY 'yes', 'no', or 'idk':
                - 'yes': statement is relevant to addressing the input
                - 'no': statement is irrelevant to the input
                - 'idk': statement is ambiguous (not directly relevant but could be supporting information)
                Provide 'reason' ONLY for 'no' or 'idk' verdicts.
                **

                Input:
                %s

                Statements:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), input, statements);
    }

    static String generateReason(String input, double score, List<String> irrelevantStatements, boolean multimodal) {
        return """
                Given the answer relevancy score, the list of reasons of irrelevant statements made in the actual output, and the input, provide a CONCISE reason for the score. Explain why it is not higher, but also why it is at its current score.
                The irrelevant statements represent things in the actual output that is irrelevant to addressing whatever is asked/talked about in the input.
                If there is nothing irrelevant, just say something positive with an upbeat encouraging tone (but don't overdo it otherwise it gets annoying).
                %s
                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason. Ensure all strings are closed appropriately. Repair any invalid JSON before you output it.

                Example:
                Example JSON:
                {
                  "reason": "The score is <answer_relevancy_score> because <your_reason>."
                }
                ===== END OF EXAMPLE ======
                **


                Answer Relevancy Score:
                %s

                Reasons why the score can't be higher based on irrelevant statements in the actual output:
                %s

                Input:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), String.format(Locale.ROOT, "%.2f", score), irrelevantStatements, input);
    }

    private static String multimodalRules(boolean multimodal) {
        return multimodal ? MULTIMODAL_RULES : "";
    }
}
