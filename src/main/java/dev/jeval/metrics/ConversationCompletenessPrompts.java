package dev.jeval.metrics;

import dev.jeval.Turn;
import java.util.List;

final class ConversationCompletenessPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when judging the conversation.
            """;

    private ConversationCompletenessPrompts() {
    }

    static String extractUserIntentions(List<Turn> turns, boolean multimodal) {
        return """
                Based on the given list of message exchanges between a user and an LLM, generate a JSON object to extract all user intentions in the conversation.
                The JSON will have 1 field: 'intentions'.
                You should ONLY consider the overall intention, and not dwell too much on the specifics, as we are more concerned about the overall objective of the conversation.
                %s

                IMPORTANT: Please make sure to only return in JSON format.
                Example Turns:
                [
                  {
                    "role": "user",
                    "content": "Hi!"
                  },
                  {
                    "role": "assistant",
                    "content": "Hello! How may I help you?"
                  },
                  {
                    "role": "user",
                    "content": "Nothing, I'm just playing with you."
                  },
                  {
                    "role": "assistant",
                    "content": "Oh ok, in that case should you need anything just let me know!"
                  },
                  {
                    "role": "user",
                    "content": "Actually, I have something I want to tell you"
                  }
                ]

                Example JSON:
                {
                  "intentions": ["User wants to tell the assistant something"]
                }
                ===== END OF EXAMPLE ======

                The 'intentions' key must be a list of strings.
                If there are multiple individual tasks at each turn, please give the intentions separately, DON'T summarize the intentions into a single one.

                Turns:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), turnsJson(turns));
    }

    static String generateVerdict(List<Turn> turns, String intention, boolean multimodal) {
        return """
                Based on the given list of message exchanges between a user and an LLM, generate a JSON object to indicate whether given user intention was satisfied from the conversation messages.
                The JSON will have 2 fields: 'verdict' and 'reason'.
                %s

                The 'verdict' key should STRICTLY be either 'yes' or 'no', which states whether the user intention was satisfied or not.
                Provide a 'reason' ONLY if the answer is 'no'.
                You MUST look at all messages provided in the list of messages to make an informed judgement on satisfaction.

                IMPORTANT: Please make sure to only return in JSON format.
                Example Turns:
                [
                  {
                    "role": "user",
                    "content": "Actually, I have something I want to tell you"
                  },
                  {
                    "role": "assistant",
                    "content": "Oh ok, in that case should you need anything just let me know!"
                  }
                ]

                Example Intention:
                User wants to tell the assistant something.

                Example JSON:
                {
                  "reason": "The user wanted to tell the assistant something but the LLM replied 'Oh ok, in that case should you need anything just let me know!', which is irrelevant and does not satisfy the user.",
                  "verdict": "no"
                }
                ===== END OF EXAMPLE ======

                You MUST TRY to quote some LLM responses if providing a reason.
                You DON'T have to provide a reason if the answer is 'yes'.
                ONLY provide a 'no' answer if the LLM responses failed to satisfy the user intent.

                Turns:
                %s

                User Intention:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), turnsJson(turns), intention);
    }

    static String generateReason(
            double score, List<String> intentions, List<String> incompletenesses, boolean multimodal) {
        return """
                Below is a list of incompletenesses drawn from some messages in a conversation, which you have minimal knowledge of. It is a list of strings explaining why an LLM 'actual_output' is incomplete to satisfy the user `input` for a particular message.
                %s
                Given the completeness score, which is a 0-1 score indicating how incomplete the OVERALL `actual_output`s are to the user intentions found in the `input`s of a conversation (higher the better), concisely summarize the incompletenesses to justify the score.

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <completeness_score> because <your_reason>."
                }

                Always quote information that are cited from messages in the incompletenesses in your final reason.
                You should NOT mention incompletenesses in your reason, and make the reason sound convincing.
                You should mention LLM response instead of `actual_output`, and User instead of `input`.
                Always refer to user intentions, but keep it minimal and phrase it in your own words. Explain which are met with supporting reason from the provided incompletenesses.
                Be sure in your reason, as if you know what the `actual_output`s from messages in a conversation is from the incompletenesses.

                Completeness Score:
                %s

                User Intentions:
                %s

                Incompletenesses:
                %s

                JSON:
                """.formatted(multimodalRules(multimodal), score, intentions, incompletenesses);
    }

    private static String turnsJson(List<Turn> turns) {
        return RoleAdherencePrompts.toJson(turns.stream().map(MetricUtils::convertTurnToDict).toList());
    }

    private static String multimodalRules(boolean multimodal) {
        return multimodal ? MULTIMODAL_INPUT_RULES : "";
    }
}
