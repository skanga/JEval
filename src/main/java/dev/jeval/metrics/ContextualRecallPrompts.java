package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class ContextualRecallPrompts {
    private ContextualRecallPrompts() {}

    static String generateVerdicts(String expectedOutput, List<String> retrievalContext, boolean multimodal) {
        var contentType = multimodal ? "sentence and image" : "sentence";
        var contentTypePlural = multimodal ? "sentences and images" : "sentences";
        var contentOr = multimodal ? "sentence or image" : "sentence";
        return """
                For EACH %s in the given expected output below, determine whether the %s can be attributed to the nodes of retrieval contexts. Please generate a list of JSON with two keys: `verdict` and `reason`.
                The `verdict` key should STRICTLY be either a 'yes' or 'no'. Answer 'yes' if the %s can be attributed to any parts of the retrieval context, else answer 'no'.
                The `reason` key should provide a reason why to the verdict. In the reason, you should aim to include the node(s) count in the retrieval context (eg., 1st node, and 2nd node in the retrieval context) that is attributed to said %s.%s You should also aim to quote the specific part of the retrieval context to justify your verdict, but keep it extremely concise and cut short the quote with an ellipsis if possible.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'verdicts' key as a list of JSON objects, each with two keys: `verdict` and `reason`.

                {
                  "verdicts": [
                    {
                      "reason": "...",
                      "verdict": "yes"
                    }
                  ]
                }

                Since you are going to generate a verdict for each sentence, the number of 'verdicts' SHOULD BE STRICTLY EQUAL to the number of %s in the expected output.
                **

                Expected Output:
                %s

                Retrieval Context:
                %s

                JSON:
                """.formatted(
                contentType,
                contentOr,
                contentOr,
                contentOr,
                multimodal ? " A node is either a string or image, but not both." : "",
                contentTypePlural,
                expectedOutput,
                retrievalContext);
    }

    static String generateReason(
            String expectedOutput,
            double score,
            List<String> supportiveReasons,
            List<String> unsupportiveReasons,
            boolean multimodal) {
        var contentType = multimodal ? "sentence or image" : "sentence";
        return """
                Given the original expected output, a list of supportive reasons, and a list of unsupportive reasons (%s deduced directly from the %s), and a contextual recall score (closer to 1 the better), summarize a CONCISE reason for the score.
                A supportive reason is the reason why a certain %s in the original expected output can be attributed to the node in the retrieval context.
                An unsupportive reason is the reason why a certain %s in the original expected output cannot be attributed to anything in the retrieval context.
                In your reason, you should %s supportive/unsupportive reasons to the %s number in expected output, and %s regarding the node number in retrieval context to support your final reason. The first mention of "node(s)" should specify "node(s) in retrieval context%s".

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <contextual_recall_score> because <your_reason>."
                }

                DO NOT mention 'supportive reasons' and 'unsupportive reasons' in your reason, these terms are just here for you to understand the broader scope of things.
                If the score is 1, keep it short and say something positive with an upbeat encouraging tone (but don't overdo it%s otherwise it gets annoying).
                **

                Contextual Recall Score:
                %s

                Expected Output:
                %s

                Supportive Reasons:
                %s

                Unsupportive Reasons:
                %s

                JSON:
                """.formatted(
                multimodal ? "which is" : "which are",
                multimodal ? "\"expected output\"" : "original expected output",
                contentType,
                contentType,
                multimodal ? "related" : "relate",
                contentType,
                multimodal ? "info" : "include info",
                multimodal ? ")" : "",
                multimodal ? "," : "",
                String.format(Locale.ROOT, "%.2f", score),
                expectedOutput,
                supportiveReasons,
                unsupportiveReasons);
    }
}
