package dev.jeval.synthesizer;

import java.util.List;

final class SynthesizerPrompts {
    private SynthesizerPrompts() {
    }

    static String generateSyntheticInputs(
            List<String> context,
            int maxGoldensPerContext,
            boolean includeExpectedOutput) {
        var shape = includeExpectedOutput
                ? "{\"data\":[{\"input\":\"...\",\"expected_output\":\"...\"}]}"
                : "{\"data\":[{\"input\":\"...\"}]}";
        return """
                Generate up to %d synthetic user inputs from the context below.
                Return only JSON in this shape: %s.

                Context:
                %s
                """.formatted(maxGoldensPerContext, shape, String.join("\n", context));
    }

    static String generateSyntheticInputsFromScratch(String scenario, String task, String inputFormat, int numGoldens) {
        return """
                Generate %d synthetic user inputs for this scenario.
                Return only JSON in this shape: {"data":[{"input":"..."}]}.

                Scenario: %s
                Task: %s
                Input format: %s
                """.formatted(numGoldens, scenario, task, inputFormat);
    }

    static String generateSyntheticInputsFromGoldens(
            List<String> inputs,
            int numGoldens,
            boolean includeExpectedOutput) {
        var shape = includeExpectedOutput
                ? "{\"data\":[{\"input\":\"...\",\"expected_output\":\"...\"}]}"
                : "{\"data\":[{\"input\":\"...\"}]}";
        return """
                Generate %d new synthetic user inputs similar in style and domain to these examples.
                Return only JSON in this shape: %s.

                Examples:
                %s
                """.formatted(numGoldens, shape, String.join("\n", inputs));
    }

    static String generateExpectedOutput(List<String> context, String input, String expectedOutputFormat) {
        return """
                Generate the expected output for the input using only the context.
                Return plain text only.%s

                Input: %s

                Context:
                %s
                """.formatted(expectedOutputFormat == null ? "" : "\nExpected output format: " + expectedOutputFormat,
                input, String.join("\n", context));
    }

    static String evolveInput(String input, Evolution evolution) {
        return """
                Rewrite the input using this evolution: %s.
                Return only JSON in this shape: {"rewritten_input":"..."}.

                Input: %s
                """.formatted(evolution.value(), input);
    }
}
