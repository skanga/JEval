package dev.jeval;

@FunctionalInterface
public interface EvaluationModel {
    String generate(String prompt);
}
