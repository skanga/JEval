package dev.jeval.metrics;

import java.util.List;

final class GEvalPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private GEvalPrompts() {
    }

    static String generateEvaluationSteps(String criteria, String parameters, boolean multimodal) {
        return """
                Given an evaluation criteria which outlines how you should judge the %s, generate 3-4 concise evaluation steps based on the criteria below. You MUST make it clear how to evaluate %s in relation to one another.
                %s

                Evaluation Criteria:
                %s

                IMPORTANT: Please make sure to only return in JSON format, with the "steps" key as a list of strings. No words or explanation is needed.
                Example JSON:
                {
                  "steps": <list_of_strings>
                }

                JSON:
                """.formatted(parameters, parameters, multimodalRules(multimodal), criteria);
    }

    static String generateEvaluationResults(
            List<String> evaluationSteps,
            String testCaseContent,
            String parameters,
            String rubric,
            List<Integer> scoreRange,
            boolean multimodal) {
        var rubricText = rubric == null ? "" : "\nRubric:\n" + rubric + "\n";
        return """
                You are an evaluator. Given the following evaluation steps%s, assess the response below and return a JSON object with two fields: score and reason.

                Score must be an integer between %d and %d.
                Reason must be a brief explanation for why the score was given. This must mention specific strengths or shortcomings, referencing relevant details from the input. Do **not** quote the score itself in the explanation.
                %s
                Only return valid JSON. Do not include any extra commentary or text.

                Evaluation Steps:
                %s
                %s
                Test Case:
                %s

                Parameters:
                %s

                **Example JSON:**
                {
                  "reason": "your concise and informative reason here",
                  "score": %d
                }

                JSON:
                """.formatted(
                rubric == null ? "" : " and rubric",
                scoreRange.get(0),
                scoreRange.get(1),
                multimodalRules(multimodal),
                GEvalUtils.numberEvaluationSteps(evaluationSteps),
                rubricText,
                testCaseContent,
                parameters,
                scoreRange.get(0));
    }

    static String generateStrictEvaluationResults(
            List<String> evaluationSteps,
            String testCaseContent,
            String parameters,
            boolean multimodal) {
        return """
                Given the evaluation steps, return a JSON with two keys: 1) a score key that is STRICTLY EITHER 1 (follows the criteria 100%% outlined in the evaluation steps), OR 0 (does not follow the criteria), and 2) a reason key, a reason for the given score, but DO NOT QUOTE THE SCORE in your reason. Please mention specific information from %s in your reason, but be very concise with it.
                %s

                Evaluation Steps:
                %s

                %s

                IMPORTANT: Please make sure to only return in JSON format, with the "score" and "reason" key. No words or explanation is needed.

                Example JSON:
                {
                  "reason": "The text does not follow the evaluation steps provided.",
                  "score": 0
                }

                JSON:
                """.formatted(parameters, multimodalRules(multimodal), GEvalUtils.numberEvaluationSteps(evaluationSteps), testCaseContent);
    }

    static String generateConversationalEvaluationSteps(String criteria, String parameters) {
        return """
                Given an evaluation criteria which outlines how you should judge a conversation between a user and an LLM chatbot using the %s fields, generate 3-4 concise evaluation steps based on the criteria below.

                Note that %s can include both turn-level fields (e.g. content, role, retrieval_context, tools_called) and conversation-level fields (e.g. scenario, expected_outcome, metadata, tags, context, chatbot_role, user_description). Evaluate each field at its correct scope: turn-level fields appear once per turn, while conversation-level fields apply to the conversation as a whole and should NOT be expected to repeat on every turn.

                Based on the evaluation criteria, you MUST make it clear how to evaluate the %s together to assess both each turn and the overall quality of the conversation.

                Evaluation Criteria:
                %s

                IMPORTANT: Please make sure to only return in JSON format, with the "steps" key as a list of strings. No words or explanation is needed.
                Example JSON:
                {
                  "steps": <list_of_strings>
                }

                JSON:
                """.formatted(parameters, parameters, parameters, criteria);
    }

    static String generateConversationalEvaluationResults(
            List<String> evaluationSteps,
            String testCaseContent,
            String parameters,
            String rubric,
            List<Integer> scoreRange) {
        var rubricText = rubric == null ? "" : "\nRubric:\n" + rubric + "\n";
        return """
                You are given a set of %s that describe how to assess a conversation between a user and an LLM chatbot. Your task is to return a JSON object with exactly two fields: score and reason.

                Score must be an integer from %d to %d.
                10 = The conversation *fully* meets the criteria described in the Evaluation Steps.
                0 = The conversation *completely fails* to meet the criteria.
                Reason must be a concise but precise explanation for the score. It must reference specific aspects of the evaluation steps%s and mention relevant details from the conversation and the given parameters. DO NOT include the score value in your explanation.

                Evaluation Steps:
                %s
                %s
                Per-turn fields:
                %s

                Parameters to consider during evaluation:
                %s

                Note: the "Per-turn fields" block lists each turn separately, while the "Conversation-level fields" block applies to the whole conversation. Do not penalize individual turns for missing conversation-level fields.

                IMPORTANT: You MUST return only a valid JSON object with the exact keys "score" and "reason". No additional text, commentary, or formatting.
                Example JSON:
                {
                  "reason": "Your concise and informative reason here.",
                  "score": 0
                }

                JSON:
                """.formatted(
                rubric == null ? "Evaluation Steps" : "Evaluation Steps and Rubric",
                scoreRange.get(0),
                scoreRange.get(1),
                rubric == null ? "" : " and rubric",
                GEvalUtils.numberEvaluationSteps(evaluationSteps),
                rubricText,
                testCaseContent,
                parameters);
    }

    static String generateArenaEvaluationSteps(String criteria, String parameters, boolean multimodal) {
        return """
                Given an evaluation criteria which outlines how you should choose the winner out of all contestants based on the %s, generate 3-4 concise evaluation steps based on the criteria below. You MUST make it clear how to evaluate %s in relation to one another.
                %s

                Evaluation Criteria:
                %s

                IMPORTANT: Please make sure to only return in JSON format, with the "steps" key as a list of strings. No words or explanation is needed.
                Example JSON:
                {
                  "steps": <list_of_strings>
                }

                JSON:
                """.formatted(parameters, parameters, multimodalRules(multimodal), criteria);
    }

    static String multimodalRules(boolean multimodal) {
        return multimodal ? MULTIMODAL_INPUT_RULES : "";
    }
}
