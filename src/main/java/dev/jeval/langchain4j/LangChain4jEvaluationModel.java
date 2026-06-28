package dev.jeval.langchain4j;

import dev.jeval.EvaluationModel;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Objects;

public final class LangChain4jEvaluationModel implements EvaluationModel {
    private final ChatModel chatModel;

    public LangChain4jEvaluationModel(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    public static LangChain4jEvaluationModel from(ChatModel chatModel) {
        return new LangChain4jEvaluationModel(chatModel);
    }

    @Override
    public String generate(String prompt) {
        return chatModel.chat(prompt);
    }
}
