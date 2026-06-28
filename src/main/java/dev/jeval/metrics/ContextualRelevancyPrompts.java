package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class ContextualRelevancyPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private ContextualRelevancyPrompts() {}

    static String generateVerdicts(String input, String context, boolean multimodal) {
        return """
                Based on the input and %s, please generate a JSON object to indicate whether %s is relevant to the provided input. The JSON will be a list of 'verdicts', with 2 mandatory fields: 'verdict' and 'statement', and 1 optional field: 'reason'.
                %s
                The 'verdict' key should STRICTLY be either 'yes' or 'no', and states whether the %s is relevant to the input.
                Provide a 'reason' ONLY IF verdict is no. You MUST quote the irrelevant parts of the %s to back up your reason.%s
                **
                IMPORTANT: Please make sure to only return in JSON format.
                Example Context: "Einstein won the Nobel Prize for his discovery of the photoelectric effect. He won the Nobel Prize in 1968. There was a cat."
                Example Input: "What were some of Einstein's achievements?"

                Example:
                {
                  "verdicts": [
                    {
                      "statement": "Einstein won the Nobel Prize for his discovery of the photoelectric effect in 1968",
                      "verdict": "yes"
                    },
                    {
                      "statement": "There was a cat.",
                      "reason": "The retrieval context contained the information 'There was a cat' when it has nothing to do with Einstein's achievements.",
                      "verdict": "no"
                    }
                  ]
                }
                **

                Input:
                %s

                Context:
                %s

                JSON:
                """.formatted(
                multimodal ? "context (image or string)" : "context",
                multimodal ? "the context" : "each statement found in the context",
                extractionInstructions(multimodal),
                multimodal ? "statement or image" : "statement",
                multimodal ? "statement or image" : "statement",
                emptyContextInstruction(multimodal),
                input,
                context);
    }

    static String generateReason(
            String input,
            double score,
            List<String> irrelevantStatements,
            List<String> relevantStatements,
            boolean multimodal) {
        return """
                Based on the given input, reasons for why the retrieval context is irrelevant to the input, the statements in the retrieval context that is actually relevant to the retrieval context, and the contextual relevancy score (the closer to 1 the better), please generate a CONCISE reason for the score.
                In your reason, you should quote data provided in the reasons for irrelevancy and relevant statements to support your point.
                %s

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <contextual_relevancy_score> because <your_reason>."
                }

                If the score is 1, keep it short and say something positive with an upbeat encouraging tone (but don't overdo it otherwise it gets annoying).
                **


                Contextual Relevancy Score:
                %s

                Input:
                %s

                Reasons for why the retrieval context is irrelevant to the input:
                %s

                Statement in the retrieval context that is relevant to the input:
                %s

                JSON:
                """.formatted(
                multimodal ? MULTIMODAL_INPUT_RULES : "",
                String.format(Locale.ROOT, "%.2f", score),
                input,
                irrelevantStatements,
                relevantStatements);
    }

    private static String extractionInstructions(boolean multimodal) {
        if (multimodal) {
            return """
                    If the context is textual, you should first extract the statements found in the context if the context, which are high level information found in the context, before deciding on a verdict and optionally a reason for each statement.
                    If the context is an image, `statement` should be a description of the image. Do not assume any information not visibly available.
                    """.strip();
        }
        return "You should first extract statements found in the context, which are high level information found in the context, before deciding on a verdict and optionally a reason for each statement.";
    }

    private static String emptyContextInstruction(boolean multimodal) {
        return multimodal ? "" : """

                If provided context contains no actual content or statements then: give "no" as a "verdict",
                put context into "statement", and "No statements found in provided context." into "reason".""";
    }
}
