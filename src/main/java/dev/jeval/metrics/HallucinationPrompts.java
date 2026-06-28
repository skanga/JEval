package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class HallucinationPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when checking factual agreement.
            """;

    private HallucinationPrompts() {}

    static String generateVerdicts(String actualOutput, List<String> contexts) {
        return """
                For each context in contexts, which is a list of strings, please generate a list of JSON objects to indicate whether the given 'actual output' agrees with EACH context. The JSON will have 2 fields: 'verdict' and 'reason'.
                %s

                The 'verdict' key should STRICTLY be either 'yes' or 'no', and states whether the given text agrees with the context.
                The 'reason' is the reason for the verdict. When the answer is 'no', try to provide a correction in the reason.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'verdicts' key as a list of JSON objects.
                Example contexts: ["Einstein won the Nobel Prize for his discovery of the photoelectric effect.", "Einstein won the Nobel Prize in 1968."]
                Example actual output: "Einstein won the Nobel Prize in 1969 for his discovery of the photoelectric effect."

                Example:
                {
                  "verdicts": [
                    {
                      "reason": "The actual output agrees with the provided context which states that Einstein won the Nobel Prize for his discovery of the photoelectric effect.",
                      "verdict": "yes"
                    },
                    {
                      "reason": "The actual output contradicts the provided context which states that Einstein won the Nobel Prize in 1968, not 1969.",
                      "verdict": "no"
                    }
                  ]
                }

                You should NOT incorporate any prior knowledge you have and take each context at face value. Since you are going to generate a verdict for each context, the number of 'verdicts' SHOULD BE STRICTLY EQUAL TO %s.
                You should FORGIVE cases where the actual output is lacking in detail, you should ONLY provide a 'no' answer if IT IS A CONTRADICTION.
                **

                Contexts:
                %s

                Actual Output:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, contexts.size(), contexts, actualOutput);
    }

    static String generateReason(double score, List<String> factualAlignments, List<String> contradictions) {
        return """
                Given a list of factual alignments and contradictions, which highlights alignment/contradictions between the `actual output` and `contexts, use it to provide a reason for the hallucination score in a CONCISELY. Note that the hallucination score ranges from 0 - 1, and the lower the better.
                %s

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <hallucination_score> because <your_reason>."
                }
                **

                Factual Alignments:
                %s

                Contradictions:
                %s

                Hallucination Score:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, factualAlignments, contradictions, String.format(Locale.ROOT, "%.2f", score));
    }
}
