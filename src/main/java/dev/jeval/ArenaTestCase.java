package dev.jeval;

import java.util.HashSet;
import java.util.List;

public record ArenaTestCase(
        List<Contestant> contestants,
        boolean multimodal) {

    public ArenaTestCase(List<Contestant> contestants) {
        this(contestants, false);
    }

    public ArenaTestCase {
        if (contestants == null || contestants.isEmpty()) {
            throw new IllegalArgumentException("'contestants' must contain at least one contestant");
        }

        var names = new HashSet<String>();
        for (var contestant : contestants) {
            if (!names.add(contestant.name())) {
                throw new IllegalArgumentException("All contestant names must be unique.");
            }
        }

        var referenceInput = contestants.getFirst().testCase().input();
        var referenceExpectedOutput = contestants.getFirst().testCase().expectedOutput();
        for (var contestant : contestants.subList(1, contestants.size())) {
            var testCase = contestant.testCase();
            if (!referenceInput.equals(testCase.input())) {
                throw new IllegalArgumentException("All contestants must have the same 'input'.");
            }
            if (!java.util.Objects.equals(referenceExpectedOutput, testCase.expectedOutput())) {
                throw new IllegalArgumentException("All contestants must have the same 'expected_output'.");
            }
        }

        contestants = List.copyOf(contestants);
        multimodal = multimodal || contestants.stream().anyMatch(contestant -> contestant.testCase().multimodal());
    }
}
