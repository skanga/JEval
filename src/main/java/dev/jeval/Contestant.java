package dev.jeval;

import java.util.Map;

public record Contestant(
        String name,
        LlmTestCase testCase,
        Map<String, Object> hyperparameters) {

    public Contestant(String name, LlmTestCase testCase) {
        this(name, testCase, null);
    }

    public Contestant {
        if (name == null) {
            throw new IllegalArgumentException("'name' must be a string");
        }
        if (testCase == null) {
            throw new IllegalArgumentException("'test_case' must be an LlmTestCase");
        }
        hyperparameters = hyperparameters == null ? null : Map.copyOf(hyperparameters);
    }
}
