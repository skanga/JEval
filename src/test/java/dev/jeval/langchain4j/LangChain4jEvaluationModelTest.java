package dev.jeval.langchain4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.metrics.AnswerRelevancyMetric;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LangChain4jEvaluationModelTest {

    @Test
    void delegatesPromptToChatModel() {
        var chatModel = new ScriptedChatModel(List.of("response"));
        var model = LangChain4jEvaluationModel.from(chatModel);

        var response = model.generate("prompt");

        assertEquals("response", response);
        assertEquals(List.of("prompt"), chatModel.prompts());
    }

    @Test
    void rejectsNullChatModel() {
        assertThrows(NullPointerException.class, () -> new LangChain4jEvaluationModel(null));
    }

    @Test
    void worksWithExistingModelBackedMetrics() {
        var chatModel = new ScriptedChatModel(List.of(
                "{\"statements\":[\"Answer includes facts.\"]}",
                "{\"verdicts\":[{\"verdict\":\"yes\"}]}",
                "{\"reason\":\"The answer is relevant.\"}"));
        var metric = new AnswerRelevancyMetric(LangChain4jEvaluationModel.from(chatModel));

        var result = metric.measure(dev.jeval.LlmTestCase.builder("question")
                .actualOutput("Answer includes facts.")
                .build());

        assertEquals(1.0, result.score());
        assertEquals("The answer is relevant.", result.reason());
        assertEquals(3, chatModel.prompts().size());
    }

    private static final class ScriptedChatModel implements ChatModel {
        private final List<String> responses;
        private final List<String> prompts = new ArrayList<>();

        ScriptedChatModel(List<String> responses) {
            this.responses = responses;
        }

        @Override
        public String chat(String message) {
            prompts.add(message);
            return responses.get(prompts.size() - 1);
        }

        List<String> prompts() {
            return prompts;
        }
    }
}
