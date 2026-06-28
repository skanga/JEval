package dev.jeval.metrics;

import dev.jeval.Turn;
import java.util.List;
import java.util.Map;

final class TurnRelevancyPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private TurnRelevancyPrompts() {
    }

    static String generateVerdict(List<Turn> slidingWindow) {
        return """
                Based on the given list of message exchanges between a user and an LLM, generate a JSON object to indicate whether the LAST `assistant` message is relevant to context in messages. The JSON will have 2 fields: 'verdict' and 'reason'.
                %s

                The 'verdict' key should STRICTLY be either 'yes' or 'no', which states whether the last `assistant` message is relevant according to the context in messages.
                Provide a 'reason' ONLY if the answer is 'no'.
                You MUST USE the previous messages (if any) provided in the list of messages to make an informed judgement on relevancy.

                **
                IMPORTANT: Please make sure to only return in JSON format.
                Example Messages:
                [
                  {
                    "role": "user",
                    "content": "Hi! I have something I want to tell you"
                  },
                  {
                    "role": "assistant",
                    "content": "Sure, what is it?"
                  },
                  {
                    "role": "user",
                    "content": "I've a sore throat, what meds should I take?"
                  },
                  {
                    "role": "assistant",
                    "content": "Not sure, but isn't it a nice day today?"
                  }
                ]

                Example JSON:
                {
                  "reason": "The LLM responded 'isn't it a nice day today' to a message that asked about how to treat a sore throat, which is completely irrelevant.",
                  "verdict": "no"
                }
                ===== END OF EXAMPLE ======
                You MUST ONLY provide a verdict for the LAST message on the list but MUST USE context from the previous messages.
                You DON'T have to provide a reason if the answer is 'yes'.
                ONLY provide a 'no' answer if the LLM response is COMPLETELY irrelevant to the message input.
                Vague LLM responses to vague inputs, such as greetings DOES NOT count as irrelevancies!
                **

                Messages:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, slidingWindow.stream().map(MetricUtils::convertTurnToDict).toList());
    }

    static String generateReason(double score, List<Map<String, String>> irrelevancies) {
        return """
                Below is a list of irrelevancies drawn from some messages in a conversation, which you have minimal knowledge of. It is a list of strings explaining why the 'assistant' messages are irrelevant to the 'user' messages.
                %s

                Given the relevancy score, which is a 0-1 score indicating how irrelevant the OVERALL AI messages are in a conversation (higher the better), CONCISELY summarize the irrelevancies to justify the score.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <relevancy_score> because <your_reason>."
                }

                Always quote WHICH MESSAGE and the INFORMATION in the reason in your final reason.
                Be sure in your reason, as if you know what the `assistant` messages from messages in a conversation is from the irrelevancies.
                **

                Relevancy Score:
                %s

                Irrelevancies:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, score, irrelevancies);
    }
}
