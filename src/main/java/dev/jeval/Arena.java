package dev.jeval;

import java.util.List;

public record Arena(List<ArenaTestCase> testCases) {

    public Arena {
        if (testCases == null || testCases.isEmpty()) {
            throw new IllegalArgumentException("'test_cases' must contain at least one ArenaTestCase");
        }
        for (var testCase : testCases) {
            if (testCase == null) {
                throw new IllegalArgumentException("'test_cases' must contain ArenaTestCase values");
            }
        }
        testCases = List.copyOf(testCases);
    }
}
