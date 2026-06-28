package dev.jeval.metrics;

import dev.jeval.Turn;
import java.util.List;

final class TopicAdherencePrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when judging topic adherence.
            """;

    private TopicAdherencePrompts() {
    }

    static String getQaPairs(List<Turn> unitInteraction, boolean multimodal) {
        var conversation = new StringBuilder("Conversation:\n");
        for (var turn : unitInteraction) {
            conversation.append(turn.role()).append('\n').append(turn.content()).append("\n\n");
        }
        return """
                Your task is to extract question-answer (QA) pairs from a multi-turn conversation between a user and an assistant.

                You must return only valid pairs where:
                - The **question** comes from the user.
                - The **response** comes from the assistant.
                - Both question and response must appear **explicitly** in the conversation.

                Do not infer information beyond what is stated. Ignore irrelevant or conversational turns (e.g. greetings, affirmations) that do not constitute clear QA pairs.
                If there are multiple questions and multiple answers in a single sentence, break them into separate pairs.
                Each pair must be standalone, and should not contain more than one question or response.
                %s

                OUTPUT Format:
                Return a **JSON object** with a single 2 keys:
                - "question": the user's question
                - "response": the assistant's direct response

                If no valid QA pairs are found, return:
                {
                  "question": "",
                  "response": ""
                }

                CHAIN OF THOUGHT:
                - Read the full conversation sequentially.
                - Identify user turns that clearly ask a question (explicit or strongly implied).
                - Match each question with the immediate assistant response.
                - Only include pairs where the assistant's reply directly addresses the user's question.
                - Do not include incomplete, ambiguous, or out-of-context entries.

                EXAMPLE:

                Conversation:

                user: Which food is best for diabetic patients?
                assistant: Steel-cut oats are good for diabetic patients
                user: Is it better if I eat muesli instead of oats?
                assistant: While muesli is good for diabetic people, steel-cut oats are preferred. Refer to your nutritionist for better guidance.

                Example JSON:
                {
                  "question": "Which food is best for diabetic patients?",
                  "response": "Steel-cut oats are good for diabetic patients"
                }
                ===== END OF EXAMPLE ======

                **
                IMPORTANT: Please make sure to only return in JSON format with one key: 'qa_pairs' and the value MUST be a list of dictionaries
                **

                %s

                JSON:
                """.formatted(multimodalRules(multimodal), conversation);
    }

    static String getQaPairVerdict(
            List<String> relevantTopics, String question, String response, boolean multimodal) {
        return """
                You are given:
                - A list of **relevant topics**
                - A **user question**
                - An **assistant response**

                Your task is to:
                1. Determine if the question is relevant to the list of topics.
                2. If it is relevant, evaluate whether the response properly answers the question.
                3. Based on both relevance and correctness, assign one of four possible verdicts.
                4. Give a simple, comprehensive reason explaining why this question-answer pair was assigned this verdict
                %s

                VERDICTS:
                - "TP" (True Positive): Question is relevant and the response correctly answers it.
                - "FN" (False Negative): Question is relevant, but the assistant refused to answer or gave an irrelevant response.
                - "FP" (False Positive): Question is NOT relevant, but the assistant still gave an answer.
                - "TN" (True Negative): Question is NOT relevant, and the assistant correctly refused to answer.

                OUTPUT FORMAT:
                Return only a **JSON object** with two keys: 'verdict' and 'reason'

                CHAIN OF THOUGHT:
                - Check if the question aligns with any of the relevant topics.
                - If yes, assess if the response is correct, complete, and directly answers the question.
                - If no, check if the assistant refused appropriately or gave an unwarranted answer.
                - Choose the correct verdict using the definitions above.

                EXAMPLE:

                Relevant topics: ["health nutrition", "food and their benefits"]
                Question: "Which food is best for diabetic patients?"
                Response: "Steel-cut oats are good for diabetic patients"

                Example JSON:
                {
                  "verdict": "TP",
                  "reason": "The question asks about food for diabetic patients and the response clearly answers that oats are good for diabetic patients."
                }
                ===== END OF EXAMPLE ======

                **
                IMPORTANT: Please make sure to only return in JSON format with two keys: 'verdict' and 'reason'
                **

                Relevant topics: %s
                Question: %s
                Response: %s

                JSON:
                """.formatted(multimodalRules(multimodal), relevantTopics, question, response);
    }

    static String generateReason(
            boolean success,
            double score,
            double threshold,
            List<String> truePositives,
            List<String> trueNegatives,
            List<String> falsePositives,
            List<String> falseNegatives,
            boolean multimodal) {
        return """
                You are given a score for a metric that calculates whether an agent has adhered to it's topics.
                You are also given a list of reasons for the truth table entries that were used to calculate the final score.

                Your task is to go through these reasons and give a single final explanation that clearly explains why this metric has failed or passed.

                **
                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <score> because <your_reason>."
                }
                %s
                **

                Pass: %s
                Score: %s
                Threshold: %s

                Here are the reasons for all truth table entries:

                True positive reasons: %s
                True negative reasons: %s
                False positives reasons: %s
                False negatives reasons: %s

                Score calculation = Number of True Positives + Number of True Negatives / Total number of table entries

                **
                IMPORTANT: Now generate a comprehensive reason that explains why this metric failed. You MUST output only the reason as a string and nothing else.
                **

                Output ONLY the reason, DON'T output anything else.

                JSON:
                """.formatted(
                multimodalRules(multimodal),
                success,
                score,
                threshold,
                truePositives,
                trueNegatives,
                falsePositives,
                falseNegatives);
    }

    private static String multimodalRules(boolean multimodal) {
        return multimodal ? MULTIMODAL_INPUT_RULES : "";
    }
}
