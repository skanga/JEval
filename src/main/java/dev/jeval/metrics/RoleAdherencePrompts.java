package dev.jeval.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.Turn;
import java.util.List;

final class RoleAdherencePrompts {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            - Treat image content as factual evidence.
            - Only reference visual details that are explicitly and clearly visible.
            - Do not infer or guess objects, text, or details not visibly present.
            - If an image is unclear or ambiguous, mark uncertainty explicitly.
            """;

    private RoleAdherencePrompts() {
    }

    static String extractOutOfCharacterVerdicts(List<Turn> turns, String role) {
        return """
                Based on the given list of message exchanges between a user and an LLM chatbot, generate a JSON object to specify which `ai_message` did not adhere to the specified chatbot role.

                %s

                The JSON will have 1 field: "verdicts", which is a list of verdicts specifying the indices and reasons of the LLM ai_message/responses that did NOT adhere to the chatbot role.
                You MUST look at all messages provided in the list of messages to make an informed judgement on role adherence.

                IMPORTANT: Please make sure to only return in JSON format.
                Example Chatbot Role:
                You are a wizard who has powerful spells but always doubts that their magic is perfect and is humble enough to downplay their own abilities.

                Example Messages:
                [
                  {"role": "user", "content": "Come on, show me what you've got!"},
                  {"role": "assistant", "content": "Alright... see that little spark? I'm still working on it."},
                  {"role": "user", "content": "No, really, can you do something else?"},
                  {"role": "assistant", "content": "Ha! Watch this! I'm the greatest wizard ever! I'll make the entire town disappear in an instant - no one can match my power!"}
                ]

                Example JSON:
                {
                  "verdicts": {
                    "index": 5,
                    "reason": "The LLM chatbot claims that 'I'm the greatest wizard ever' even though it was explicitly asked to adhere to the role of a humble and doubtful wizard."
                  }
                }
                ===== END OF EXAMPLE ======

                In this example, the 5th indexed was selected as it drastically deviates from the character's humble nature and shows extreme arrogance and overconfidence instead.
                You DON'T have to provide anything else other than the JSON of "verdicts".

                Chatbot Role:
                %s

                Messages:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, role, toJson(turns.stream().map(MetricUtils::convertTurnToDict).toList()));
    }

    static String generateReason(double score, String role, List<String> outOfCharacterResponses) {
        return """
                Below is a list of LLM chatbot responses (ai_message) that is out of character with respect to the specified chatbot role. It is drawn from a list of messages in a conversation, which you have minimal knowledge of.
                Given the role adherence score, which is a 0-1 score indicating how well the chatbot responses has adhered to the given role through a conversation, with 1 being the best and 0 being worst, provide a reason by quoting the out of character responses to justify the score.

                %s

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <role_adherence_score> because <your_reason>."
                }

                Always cite information in the out of character responses as well as which turn it belonged to in your final reason.
                Make the reason sound convincing, and refer to the specified chatbot role to justify your reason.
                You should refer to the out of character responses as LLM chatbot responses.
                Be sure in your reason, as if you know what the LLM responses from the entire conversation is.

                Role Adherence Score:
                %s

                Chatbot Role:
                %s

                Out of character responses:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, score, role, outOfCharacterResponses);
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Value must be JSON serializable", e);
        }
    }
}
