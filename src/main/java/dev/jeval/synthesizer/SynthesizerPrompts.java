package dev.jeval.synthesizer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class SynthesizerPrompts {
    private SynthesizerPrompts() {
    }

    static String generateSyntheticInputs(
            List<String> context,
            int maxGoldensPerContext,
            boolean includeExpectedOutput) {
        return generateSyntheticInputs(context, maxGoldensPerContext, includeExpectedOutput, List.of());
    }

    static String generateSyntheticInputs(
            List<String> context,
            int maxGoldensPerContext,
            boolean includeExpectedOutput,
            List<String> availableSourceFiles) {
        return generateSyntheticInputs(
                context, maxGoldensPerContext, includeExpectedOutput, availableSourceFiles, null);
    }

    static String generateSyntheticInputs(
            List<String> context,
            int maxGoldensPerContext,
            boolean includeExpectedOutput,
            List<String> availableSourceFiles,
            Integer targetFilesPerContext) {
        return generateSyntheticInputs(
                context,
                maxGoldensPerContext,
                includeExpectedOutput,
                availableSourceFiles,
                availableSourceFiles,
                targetFilesPerContext);
    }

    static String generateSyntheticInputs(
            List<String> context,
            int maxGoldensPerContext,
            boolean includeExpectedOutput,
            List<String> availableSourceFiles,
            List<String> chunkSourceFiles,
            Integer targetFilesPerContext) {
        var shape = includeExpectedOutput
                ? "{\"data\":[{\"input\":\"...\",\"expected_output\":\"...\"}]}"
                : "{\"data\":[{\"input\":\"...\"}]}";
        var sourceFilesSection = sourceFilesSection(context, availableSourceFiles, "input", targetFilesPerContext);
        return """
                Generate up to %d synthetic user inputs from the context below.
                Return only JSON in this shape: %s.
                %s

                Context:
                %s
                """.formatted(
                maxGoldensPerContext,
                shape,
                sourceFilesSection,
                String.join("\n", formatContextWithSources(context, chunkSourceFiles)));
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

    static String generateTextToSqlInputs(List<String> context, int maxGoldensPerContext) {
        return """
                Based on the given context, which is a SQL table schema, please generate a list of JSON objects with `input` keys.
                The `input` can either be a question or a statement that can be addressed by the given schema.

                IMPORTANT: Please make sure to only return in JSON format, with the 'data' key as a list of JSON objects.
                You MUST TRY to generate %d data points, unless the `input` is getting repetitive.
                You should NOT incorporate any prior knowledge you have and take each context at face value.
                You MUST include at least one statement as the input.
                `input` MUST be a STRING.

                Example context: [
                  "Table: Customers",
                  "Column: CustomerID, Type: INT, Description: Unique identifier for each customer",
                  "Column: City, Type: VARCHAR, Description: City where the customer resides"
                ]
                Example max goldens per context: 2
                Example JSON: {"data":[{"input":"Show me all the customers who live in New York."},{"input":"List the first and last names of all customers."}]}

                Max Goldens Per Context:
                %d

                Context:
                %s

                JSON:
                """.formatted(maxGoldensPerContext, maxGoldensPerContext, String.join("\n", context));
    }

    static String generateTextToSqlExpectedOutput(String input, List<String> context) {
        return """
                Given the input, which may be a question or a statement addressable by the schema provided in the context,
                generate a JSON object with a key 'sql'. This key should contain the corresponding SQL statement that accurately and efficiently responds to the input.

                IMPORTANT: The output must be in JSON format, with the 'sql' key only.

                Example Context: [
                  "Table: Customers",
                  "Column: CustomerID, Type: INT, Description: Unique identifier for each customer",
                  "Column: City, Type: VARCHAR, Description: City where the customer resides"
                ]
                Example Input: "Show me all the customers who live in New York."
                Example JSON: {"sql":"SELECT * FROM Customers WHERE City = 'New York';"}

                Context:
                %s

                Input:
                %s

                JSON:
                """.formatted(String.join("\n", context), input);
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

    static String extractPromptStructureFromInputs(List<String> inputs) {
        return """
                Analyze the following user inputs and infer the common prompt structure.
                Return only JSON in this shape: {"scenario":"...","task":"...","input_format":"..."}.

                Inputs:
                %s
                """.formatted(String.join("\n", inputs));
    }

    static String extractConversationalStructureFromScenarios(List<String> scenarios) {
        return """
                Analyze the following conversational scenarios and extract the common structural elements.
                Return only JSON in this shape: {"scenario_context":"...","conversational_task":"...","participant_roles":"..."}.

                Example Scenarios:
                %s
                """.formatted(String.join("\n", scenarios.stream().map(scenario -> "- " + scenario).toList()));
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

    static String evaluateSyntheticInput(String input) {
        return """
                Evaluate the provided synthetic query for clarity and answerability.
                Return only JSON in this shape: {"feedback":"...","score":0.0}.
                The score must be between 0 and 1, where 1 is clear, self-contained, and answerable.

                Query:
                %s
                """.formatted(input);
    }

    static String rewriteSyntheticInput(List<String> context, String input, String feedback) {
        return """
                Rewrite the synthetic query using only the context and feedback.
                Return only JSON in this shape: {"rewritten_input":"..."}.

                Context:
                %s

                Query:
                %s

                Feedback:
                %s
                """.formatted(String.join("\n", context == null ? List.of() : context), input, feedback);
    }

    static String rewriteEvolvedInput(String input, StylingConfig stylingConfig) {
        return """
                Given the evolved input, generate a JSON object with an `input` key.
                Change the evolved input as little as possible while matching the scenario, task, and input format.

                Evolved Input:
                %s

                Scenario: %s
                Task: %s
                Input Format: %s
                """.formatted(
                input,
                stylingConfig == null ? "" : stylingConfig.scenario(),
                stylingConfig == null ? "" : stylingConfig.task(),
                stylingConfig == null ? "" : stylingConfig.inputFormat());
    }

    static String generateSyntheticConversationalScenarios(
            List<String> context,
            int maxGoldensPerContext,
            ConversationalStylingConfig stylingConfig,
            boolean includeExpectedOutcome) {
        return generateSyntheticConversationalScenarios(
                context, maxGoldensPerContext, stylingConfig, includeExpectedOutcome, List.of());
    }

    static String generateSyntheticConversationalScenarios(
            List<String> context,
            int maxGoldensPerContext,
            ConversationalStylingConfig stylingConfig,
            boolean includeExpectedOutcome,
            List<String> availableSourceFiles) {
        return generateSyntheticConversationalScenarios(
                context, maxGoldensPerContext, stylingConfig, includeExpectedOutcome, availableSourceFiles, null);
    }

    static String generateSyntheticConversationalScenarios(
            List<String> context,
            int maxGoldensPerContext,
            ConversationalStylingConfig stylingConfig,
            boolean includeExpectedOutcome,
            List<String> availableSourceFiles,
            Integer targetFilesPerContext) {
        return generateSyntheticConversationalScenarios(
                context,
                maxGoldensPerContext,
                stylingConfig,
                includeExpectedOutcome,
                availableSourceFiles,
                availableSourceFiles,
                targetFilesPerContext);
    }

    static String generateSyntheticConversationalScenarios(
            List<String> context,
            int maxGoldensPerContext,
            ConversationalStylingConfig stylingConfig,
            boolean includeExpectedOutcome,
            List<String> availableSourceFiles,
            List<String> chunkSourceFiles,
            Integer targetFilesPerContext) {
        var shape = includeExpectedOutcome
                ? "{\"data\":[{\"scenario\":\"...\",\"turns\":[{\"role\":\"user\",\"content\":\"...\"},{\"role\":\"assistant\",\"content\":\"...\"}],\"expected_outcome\":\"...\"}]}"
                : "{\"data\":[{\"scenario\":\"...\",\"turns\":[{\"role\":\"user\",\"content\":\"...\"},{\"role\":\"assistant\",\"content\":\"...\"}]}]}";
        var sourceFilesSection = sourceFilesSection(context, availableSourceFiles, "scenario", targetFilesPerContext);
        return """
                Generate up to %d synthetic multi-turn conversation scenarios from the context below.
                Return only JSON in this shape: %s.
                %s

                Scenario context: %s
                Conversational task: %s
                Participant roles: %s

                Context:
                %s
                """.formatted(maxGoldensPerContext, shape,
                sourceFilesSection,
                stylingConfig == null ? "" : stylingConfig.scenarioContext(),
                stylingConfig == null ? "" : stylingConfig.conversationalTask(),
                stylingConfig == null ? "" : stylingConfig.participantRoles(),
                String.join("\n", formatContextWithSources(context, chunkSourceFiles)));
    }

    private static String sourceFilesSection(
            List<String> context,
            List<String> availableSourceFiles,
            String itemKey,
            Integer targetFilesPerContext) {
        var sourceFiles = distinctSourceFiles(availableSourceFiles);
        if (sourceFiles.size() < 2) {
            return "";
        }
        var targetInstruction = targetFilesPerContext == null
                ? ""
                : "\nEach generated `" + itemKey + "` MUST meaningfully combine information from at least "
                        + targetFilesPerContext
                        + " different source files. Ensure ATLEAST "
                        + targetFilesPerContext
                        + " source files are used in the `" + itemKey + "`.";
        var exampleSources = sourceFiles.stream()
                .limit(Math.max(2, targetFilesPerContext == null ? 2 : targetFilesPerContext))
                .toList();
        var crossFileObjective = "scenario".equals(itemKey)
                ? "Your goal is to design powerful, cross-file scenarios whose conversations require reasoning across different files, not single-file recall."
                : "Your goal is to generate powerful, cross-file goldens that test reasoning across different files, not single-file recall.";
        return """

                This context is constructed from multiple files with these source labels: %s.
                Each chunk in the context is prefixed with its origin as `[SOURCE: <label>]`.
                %s
                %s
                For each generated item, include a `used_source_files` key listing only the source labels actually used to form that `%s`.
                `used_source_files` MUST be a JSON array of strings and each value MUST come from: %s.
                Example item: {"%s": "...", "used_source_files": %s}.
                """.formatted(
                sourceFiles,
                crossFileObjective,
                targetInstruction,
                itemKey,
                sourceFiles,
                itemKey,
                jsonStringArray(exampleSources));
    }

    private static String jsonStringArray(List<String> values) {
        var items = new ArrayList<String>();
        for (var value : values) {
            items.add("\"" + escapeJson(value) + "\"");
        }
        return "[" + String.join(", ", items) + "]";
    }

    private static String escapeJson(String value) {
        var escaped = new StringBuilder();
        for (var i = 0; i < value.length(); i++) {
            var ch = value.charAt(i);
            switch (ch) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private static List<String> formatContextWithSources(List<String> context, List<String> sourceFiles) {
        var distinctSourceFiles = distinctAlignedSourceFiles(context, sourceFiles);
        if (distinctSourceFiles.size() < 2) {
            return context == null ? List.of() : context;
        }
        var formatted = new ArrayList<String>();
        for (var i = 0; i < context.size(); i++) {
            formatted.add("[SOURCE: " + sourceFiles.get(i) + "] " + context.get(i));
        }
        return List.copyOf(formatted);
    }

    private static List<String> distinctSourceFiles(List<String> sourceFiles) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(sourceFiles));
    }

    private static List<String> distinctAlignedSourceFiles(List<String> context, List<String> sourceFiles) {
        if (sourceFiles == null || sourceFiles.isEmpty()) {
            return List.of();
        }
        if (context != null && context.size() != sourceFiles.size()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(sourceFiles));
    }

    static String evaluateSyntheticScenario(String scenario) {
        return """
                Evaluate the provided conversational scenario for clarity, conversational nature, and appropriateness.
                Return only JSON in this shape: {"feedback":"...","score":0.0}.
                The score must be between 0 and 1, where 1 is a clear, natural multi-turn scenario.

                Scenario:
                %s
                """.formatted(scenario);
    }

    static String rewriteSyntheticScenario(List<String> context, String scenario, String feedback) {
        return """
                Rewrite the conversational scenario using only the context and feedback.
                Return only JSON in this shape: {"rewritten_scenario":"..."}.

                Context:
                %s

                Scenario:
                %s

                Feedback:
                %s
                """.formatted(String.join("\n", context == null ? List.of() : context), scenario, feedback);
    }

    static String rewriteEvolvedScenario(String scenario, ConversationalStylingConfig stylingConfig) {
        return """
                Given the evolved conversational scenario, generate a JSON object with a `scenario` key.
                Change the evolved scenario as little as possible while matching the participant roles, scenario context, and conversational task.

                Evolved Scenario:
                %s

                Participant Roles: %s
                Scenario Context: %s
                Conversational Task: %s
                """.formatted(
                scenario,
                stylingConfig == null ? "" : stylingConfig.participantRoles(),
                stylingConfig == null ? "" : stylingConfig.scenarioContext(),
                stylingConfig == null ? "" : stylingConfig.conversationalTask());
    }

    static String generateSyntheticConversationalScenariosFromScratch(
            ConversationalStylingConfig stylingConfig,
            int numGoldens) {
        return """
                Generate %d synthetic multi-turn conversation scenarios.
                Return only JSON in this shape: {"data":[{"scenario":"...","turns":[{"role":"user","content":"..."},{"role":"assistant","content":"..."}],"expected_outcome":"..."}]}.

                Scenario context: %s
                Conversational task: %s
                Participant roles: %s
                """.formatted(numGoldens, stylingConfig.scenarioContext(),
                stylingConfig.conversationalTask(), stylingConfig.participantRoles());
    }

    static String generateConversationalExpectedOutcome(
            String scenario,
            List<String> context,
            String expectedOutcomeFormat) {
        return """
                Generate the expected outcome for this conversation scenario using only the context.
                Return plain text only.%s

                Scenario: %s

                Context:
                %s
                """.formatted(expectedOutcomeFormat == null ? "" : "\nExpected outcome format: " + expectedOutcomeFormat,
                scenario, String.join("\n", context == null ? List.of() : context));
    }

    static String evolveInput(String input, Evolution evolution) {
        return """
                Rewrite the input using this evolution: %s.
                Return only JSON in this shape: {"rewritten_input":"..."}.

                Input: %s
                """.formatted(evolution.value(), input);
    }

    static String evolveScenario(String scenario, List<String> context, Evolution evolution) {
        return """
                Rewrite the conversational scenario using this evolution: %s.
                Preserve factual correctness using only the context.
                Return only JSON in this shape: {"rewritten_scenario":"..."}.

                Scenario: %s

                Context:
                %s
                """.formatted(evolution.value(), scenario, String.join("\n", context == null ? List.of() : context));
    }
}
