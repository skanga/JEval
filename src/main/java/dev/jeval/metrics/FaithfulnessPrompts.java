package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class FaithfulnessPrompts {
    private static final String FORMAT_INSTRUCTION = """

            Expected JSON format:
            {
            "verdicts": [
            {
            "verdict": "yes"
            },
            {
            "reason": <explanation_for_contradiction>,
            "verdict": "no"
            },
            {
            "reason": <explanation_for_uncertainty>,
            "verdict": "idk"
            }
            ]
            }
            """;
    private static final String TEXT_GUIDELINES = """

            Generate ONE verdict per claim - length of 'verdicts' MUST equal number of claims.
            No 'reason' needed for 'yes' verdicts.
            Only use 'no' if retrieval context DIRECTLY CONTRADICTS the claim - never use prior knowledge.
            Use 'idk' for claims not backed up by context OR factually incorrect but non-contradictory - do not assume your knowledge.
            Vague/speculative language in claims (e.g. 'may have', 'possibility') does NOT count as contradiction.
            """;
    private static final String MULTIMODAL_GUIDELINES = """

            The length of 'verdicts' SHOULD BE STRICTLY EQUAL to that of claims.
            You DON'T have to provide a reason if the answer is 'yes'.
            ONLY provide a 'no' answer if the retrieval context DIRECTLY CONTRADICTS the claims. YOU SHOULD NEVER USE YOUR PRIOR KNOWLEDGE IN YOUR JUDGEMENT.
            Claims made using vague, suggestive, speculative language such as 'may have', 'possibility due to', does NOT count as a contradiction.
            Claims that is not backed up due to a lack of information/is not mentioned in the retrieval contexts MUST be answered 'idk', otherwise I WILL DIE.
            If there are clear contradictions or any data or images that's not mentioned in the retrieval context, just provide 'no'.
            """;

    private FaithfulnessPrompts() {}

    static String generateTruths(List<String> retrievalContext, Integer truthsExtractionLimit, boolean multimodal) {
        return """
                Based on the given %s, please generate a comprehensive list of%s, that can inferred from the provided %s.%s
                These truths, MUST BE COHERENT. They must NOT be taken out of context.

                Example:
                Example Text:
                "Albert Einstein, the genius often associated with wild hair and mind-bending theories, famously won the Nobel Prize in Physics - though not for his groundbreaking work on relativity, as many assume. Instead, in 1968, he was honored for his discovery of the photoelectric effect, a phenomenon that laid the foundation for quantum mechanics."

                Example JSON:
                {
                  "truths": [
                    "Einstein won the noble prize for his discovery of the photoelectric effect in 1968.",
                    "The photoelectric effect is a phenomenon that laid the foundation for quantum mechanics."
                  ]
                }
                ===== END OF EXAMPLE ======

                **
                IMPORTANT: Please make sure to only return in JSON format, with the "truths" key as a list of strings. No words or explanation is needed.
                Only include truths that are factual, BUT IT DOESN'T MATTER IF THEY ARE FACTUALLY CORRECT.
                **

                %s:
                %s

                JSON:
                """.formatted(
                multimodal ? "excerpt (text and images)" : "text",
                truthsLimitPhrase(truthsExtractionLimit),
                multimodal ? "excerpt" : "text",
                truthsMultimodalInstruction(multimodal),
                multimodal ? "Excerpt" : "Text",
                String.join("\n\n", retrievalContext));
    }

    static String generateClaims(String actualOutput, boolean multimodal) {
        return """
                Based on the given %s, please extract a comprehensive list of FACTUAL, undisputed truths, that can inferred from the provided actual AI output. %s
                These truths, MUST BE COHERENT, and CANNOT be taken out of context.

                Example:
                Example Text:
                "Albert Einstein, the genius often associated with wild hair and mind-bending theories, famously won the Nobel Prize in Physics - though not for his groundbreaking work on relativity, as many assume. Instead, in 1968, he was honored for his discovery of the photoelectric effect, a phenomenon that laid the foundation for quantum mechanics."

                Example JSON:
                {
                  "claims": [
                    "Einstein won the noble prize for his discovery of the photoelectric effect in 1968.",
                    "The photoelectric effect is a phenomenon that laid the foundation for quantum mechanics."
                  ]
                }
                ===== END OF EXAMPLE ======

                **
                IMPORTANT: Please make sure to only return in JSON format, with the "claims" key as a list of strings. No words or explanation is needed.
                Only include claims that are factual, BUT IT DOESN'T MATTER IF THEY ARE FACTUALLY CORRECT. The claims you extract should include the full context it was presented in, NOT cherry picked facts.
                You should NOT include any prior knowledge, and take the text at face value when extracting claims.
                You should be aware that it is an AI that is outputting these claims.
                **

                %s:
                %s

                JSON:
                """.formatted(
                multimodal ? "excerpt" : "text",
                claimsMultimodalInstruction(multimodal),
                multimodal ? "Excerpt" : "AI Output",
                actualOutput);
    }

    static String generateVerdicts(List<String> truths, List<String> claims, boolean multimodal) {
        return """
                Based on the given claims, which is a list of strings, generate a list of JSON objects to indicate whether EACH claim contradicts any facts in the retrieval context. The JSON will have 2 fields: 'verdict' and 'reason'.
                The 'verdict' key should STRICTLY be either 'yes', 'no', or 'idk', which states whether the given claim agrees with the context.
                Provide a 'reason' ONLY if the answer is 'no' or 'idk'.
                The provided claim is drawn from the actual output. Try to provide a correction in the reason using the facts in the retrieval context.
                %s
                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'verdicts' key as a list of JSON objects.
                %s
                **

                Retrieval Contexts:
                %s

                Claims:
                %s

                JSON:
                """.formatted(FORMAT_INSTRUCTION, multimodal ? MULTIMODAL_GUIDELINES : TEXT_GUIDELINES, String.join("\n\n", truths), claims);
    }

    static String generateReason(double score, List<String> contradictions) {
        return """
                Below is a list of Contradictions. It is a list of strings explaining why the 'actual output' does not align with the information presented in the 'retrieval context'. Contradictions happen in the 'actual output', NOT the 'retrieval context'.
                Given the faithfulness score, which is a 0-1 score indicating how faithful the `actual output` is to the retrieval context (higher the better), CONCISELY summarize the contradictions to justify the score.

                Expected JSON format:
                {
                  "reason": "The score is <faithfulness_score> because <your_reason>."
                }

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                If there are no contradictions, just say something positive with an upbeat encouraging tone (but don't overdo it otherwise it gets annoying).
                Your reason MUST use information in `contradiction` in your reason.
                Be sure in your reason, as if you know what the actual output is from the contradictions.
                **

                Faithfulness Score:
                %s

                Contradictions:
                %s

                JSON:
                """.formatted(String.format(Locale.ROOT, "%.2f", score), contradictions);
    }

    private static String truthsLimitPhrase(Integer extractionLimit) {
        if (extractionLimit == null) {
            return " FACTUAL, undisputed truths";
        }
        if (extractionLimit == 1) {
            return " the single most important FACTUAL, undisputed truth";
        }
        return " the " + Math.max(extractionLimit, 0) + " most important FACTUAL, undisputed truths per document";
    }

    private static String truthsMultimodalInstruction(boolean multimodal) {
        return multimodal ? " The excerpt may contain both text and images." : "";
    }

    private static String claimsMultimodalInstruction(boolean multimodal) {
        return multimodal ? "The excerpt may contain both text and images, so extract claims from all provided content." : "";
    }
}
