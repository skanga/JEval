package dev.jeval.metrics;

import java.util.List;
import java.util.Locale;

final class SummarizationPrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality for the summarization task.
            """;

    private SummarizationPrompts() {}

    static String generateQuestions(String text, int n) {
        return """
                Based on the given text, generate %s closed-ended questions that can be answered with either a 'yes' or 'no'.
                The questions generated should ALWAYS result in a 'yes' based on the given text.
                %s

                ** IMPORTANT
                Only return a JSON with a 'questions' key, which is a list of strings.
                The questions have to be STRICTLY closed ended.
                The given text should be able to answer 'yes' for each question.
                **
                Text:
                %s

                JSON:
                """.formatted(n, MULTIMODAL_INPUT_RULES, text);
    }

    static String generateAnswers(String text, List<String> questions) {
        return """
                Based on the list of close-ended 'yes' or 'no' questions, generate a JSON with key 'answers', which is a list of strings that determines whether the provided text contains sufficient information to answer EACH question.
                Answers should STRICTLY be either 'yes' or 'no'.
                Answer 'no' if the provided text does not contain enough information to answer the question.
                %s

                IMPORTANT: Please make sure to only return in JSON format, with the 'answers' key as a list of strings.

                Example:
                Example Text: Mario and Luigi were best buds but since Luigi had a crush on Peach Mario ended up killing him.
                Example Questions: ["Are there enough information about Luigi and Mario?"]
                Example Answers:
                {
                  "answers": ["yes"]
                }

                The length of 'answers' SHOULD BE STRICTLY EQUAL to that of questions.

                Text:
                %s

                Questions:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, text, questions);
    }

    static String generateAlignmentVerdicts(List<String> claims, String originalText) {
        return """
                Based on the given summary claims, which is a list of strings, generate a list of JSON objects to indicate whether EACH piece of info contradicts any facts in the original text. The JSON will have 2 fields: 'verdict' and 'reason'.
                %s
                The 'verdict' key should STRICTLY be either 'yes', 'no', or 'idk', which states whether the given summary claim agrees with the original text.
                Provide a 'reason' ONLY if the answer is 'no' OR 'idk'.
                The provided summary claims is drawn from the summary. Try to provide a correction in the reason using the facts in the original text.

                IMPORTANT: Please make sure to only return in JSON format, with the 'verdicts' key as a list of JSON objects.

                Example Original Text: "Einstein won the Nobel Prize for his discovery of the photoelectric effect. Einstein won the Nobel Prize in 1968. Einstein is a German Scientist."
                Example Summary Claims: ["Barack Obama is a caucasian male.", "Zurich is a city in London", "Einstein won the Nobel Prize for the discovery of the photoelectric effect which may have contributed to his fame.", "Einstein won the Nobel Prize in 1969 for his discovery of the photoelectric effect.", "Einstein was a German chef."]

                The length of 'verdicts' SHOULD BE STRICTLY EQUAL to that of summary claims.
                You DON'T have to provide a reason if the answer is 'yes'.
                ONLY provide a 'no' answer if the summary DIRECTLY CONTRADICTS the claims. YOU SHOULD NEVER USE YOUR PRIOR KNOWLEDGE IN YOUR JUDGEMENT.
                Claims made using vague, suggestive, speculative language such as 'may have', 'possibility due to', does NOT count as a contradiction.
                Claims that are not backed up due to a lack of information or are not mentioned in the summary MUST be answered 'idk'.

                Original Text:
                %s

                Summary Claims:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, originalText, claims);
    }

    static String generateReason(
            double score, List<String> contradictions, List<String> redundancies, List<String> questions) {
        return """
                You will be given the following: 1) information in the summary contradicting the original text, 2) extra information in the summary not mentioned in the original text, 3) [Optional] questions cannot be answered by the summary. Your task is to explain the quality of this summarization task.
                Given the summarization score, which is a 0-1 score indicating how good the summary is to the original text (higher the better), CONCISELY summarize the provided information to justify the score.
                %s

                IMPORTANT: Please make sure to only return in JSON format, with the 'reason' key providing the reason.
                Example JSON:
                {
                  "reason": "The score is <summarization_score> because <your_reason>."
                }

                For 'None' values in contradictions, extra information, or questions that the original text can answer but not the summary, DON'T mention anything and instead offer some praise.
                Be sure in your reason, as if you know what the summary and original text is.

                Summarization Score:
                %s

                Contradicting Information in the original text:
                %s

                Extra Information not mentioned in the original text:
                %s

                Questions the original text can answer but not the summary:
                %s

                JSON:
                """.formatted(
                MULTIMODAL_INPUT_RULES,
                String.format(Locale.ROOT, "%.2f", score),
                contradictions,
                redundancies,
                questions);
    }
}
