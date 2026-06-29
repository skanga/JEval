package dev.jeval;

import java.util.List;

@FunctionalInterface
public interface EvaluationModel {
    String generate(String prompt);

    default List<String> batchGenerate(List<String> prompts) {
        return prompts.stream().map(this::generate).toList();
    }
}
