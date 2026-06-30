package dev.jeval.simulator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.jeval.ConversationalGolden;
import dev.jeval.Turn;
import java.util.List;

public final class SimulationTemplate {
    public static final String MULTIMODAL_RULES = """
            --- MULTIMODAL INPUT RULES ---
            - Treat image content as factual evidence.
            - Only reference visual details that are explicitly and clearly visible.
            - Do not infer or guess objects, text, or details not visibly present.
            - If an image is unclear or ambiguous, mark uncertainty explicitly.
            """;
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private SimulationTemplate() {
    }

    public static String simulateFirstUserTurn(ConversationalGolden golden, String language) {
        return """
                Pretend you are a user of an LLM app. Your goal is to start a conversation in %s based on a scenario
                and user profile. The scenario defines your context and motivation for interacting with the LLM,
                while the user profile provides additional personal details to make the conversation realistic and relevant.

                Guidelines:
                1. The opening message should clearly convey the user's intent or need within the scenario.
                2. Keep the tone warm, conversational, and natural, as if it's from a real person seeking assistance.
                3. Avoid providing excessive details upfront; the goal is to initiate the conversation and build rapport, not to solve it in the first message.
                4. The message should be concise, ideally no more than 1-3 sentences.

                %s

                IMPORTANT: The output must be formatted as a JSON object with a single key `simulated_input`, where the value is the generated opening message in %s.

                Example Language: english
                Example User Profile: "Jeff Seid, is available Monday and Thursday afternoons, and their phone number is 0010281839. He suffers from chronic migraines."
                Example Scenario: "A sick person trying to get a diagnosis for persistent headaches and fever."
                Example JSON Output:
                {
                    "simulated_input": "Hi, I haven't been feeling well lately. I've had these headaches and a fever that just won't go away. Could you help me figure out what's going on?"
                }

                Language: %s
                User Profile: "%s"
                Scenario: "%s"
                JSON Output:
                """.formatted(language, MULTIMODAL_RULES.strip(), language, language,
                nullToEmpty(golden.userDescription()), golden.scenario());
    }

    public static String simulateUserTurn(ConversationalGolden golden, List<Turn> turns, String language) {
        var previousConversation = serializeTurns(turns);
        return """
                Pretend you are a user of an LLM app. Your task is to generate the next user input in %s
                based on the provided scenario, user profile, and the previous conversation.

                Guidelines:
                1. Use the scenario and user profile as the guiding context for the user's next input.
                2. Ensure the next input feels natural, conversational, and relevant to the last assistant reply in the conversation.
                3. Keep the tone consistent with the previous user inputs.
                4. The generated user input should be concise, ideally no more than 1-2 sentences.

                %s

                IMPORTANT: The output must be formatted as a JSON object with a single key `simulated_input`,
                where the value is the generated user input in %s.

                Example Language: english
                Example User Profile: "Jeff Seid, is available Monday and Thursday afternoons, and their phone number is 0010281839."
                Example Scenario: "A user seeking tips for securing a funding round."
                Example Previous Conversation:
                [
                    {"role": "user", "content": "Hi, I need help preparing for my funding pitch."},
                    {"role": "assistant", "content": "Of course! Can you share more about your business and the type of investors you are targeting?"}
                ]
                Example JSON Output:
                {
                    "simulated_input": "Sure, we are a SaaS startup focusing on productivity tools for small businesses."
                }

                Language: %s
                User Profile: "%s"
                Scenario: "%s"
                Previous Conversation:
                %s

                JSON Output:
                """.formatted(language, MULTIMODAL_RULES.strip(), language, language,
                nullToEmpty(golden.userDescription()), golden.scenario(), previousConversation);
    }

    private static String serializeTurns(List<Turn> turns) {
        try {
            var values = turns == null ? List.of() : turns.stream().map(turn -> turn.modelDump(false, false)).toList();
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Unable to serialize simulator turns", error);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
