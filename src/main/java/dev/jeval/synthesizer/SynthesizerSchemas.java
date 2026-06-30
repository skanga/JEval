package dev.jeval.synthesizer;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.jeval.Turn;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

final class SynthesizerSchemas {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SynthesizerSchemas() {
    }

    static List<SyntheticData> parseSyntheticData(String json) {
        try {
            return JSON.readValue(json, SyntheticDataList.class).data();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic data JSON", error);
        }
    }

    static String parseRewrittenInput(String json) {
        try {
            return JSON.readValue(json, RewrittenInput.class).rewrittenInput();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse rewritten input JSON", error);
        }
    }

    static String parseInput(String json) {
        try {
            return JSON.readValue(json, SyntheticInput.class).input();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic input JSON", error);
        }
    }

    static StylingConfig parseStylingConfig(String json) {
        try {
            var styling = JSON.readValue(json, PromptStyling.class);
            return new StylingConfig(styling.scenario(), styling.task(), styling.inputFormat(), null);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse prompt styling JSON", error);
        }
    }

    static InputFeedback parseInputFeedback(String json) {
        try {
            return JSON.readValue(json, InputFeedback.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse input feedback JSON", error);
        }
    }

    static ScenarioFeedback parseScenarioFeedback(String json) {
        try {
            return JSON.readValue(json, ScenarioFeedback.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse scenario feedback JSON", error);
        }
    }

    static String parseRewrittenScenario(String json) {
        try {
            return JSON.readValue(json, RewrittenScenario.class).rewrittenScenario();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse rewritten scenario JSON", error);
        }
    }

    static String parseScenario(String json) {
        try {
            return JSON.readValue(json, SyntheticScenario.class).scenario();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic scenario JSON", error);
        }
    }

    static List<ConversationalData> parseConversationalData(String json) {
        try {
            return JSON.readValue(json, ConversationalDataList.class).data();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse conversational synthetic data JSON", error);
        }
    }

    record SyntheticData(
            String input,
            @JsonAlias("expected_output") String expectedOutput,
            @JsonAlias("used_source_files") List<String> usedSourceFiles) {
    }

    private record SyntheticDataList(List<SyntheticData> data) {
    }

    private record RewrittenInput(@JsonAlias("rewritten_input") String rewrittenInput) {
    }

    private record SyntheticInput(String input) {
    }

    private record PromptStyling(
            String scenario,
            String task,
            @JsonAlias("input_format") String inputFormat) {
    }

    record InputFeedback(String feedback, double score) {
    }

    record ScenarioFeedback(String feedback, double score) {
    }

    private record RewrittenScenario(@JsonAlias("rewritten_scenario") String rewrittenScenario) {
    }

    private record SyntheticScenario(String scenario) {
    }

    record ConversationalData(
            String scenario,
            @JsonAlias("expected_outcome") String expectedOutcome,
            @JsonAlias("user_description") String userDescription,
            List<Turn> turns,
            @JsonAlias("used_source_files") List<String> usedSourceFiles) {
    }

    private record ConversationalDataList(List<ConversationalData> data) {
    }
}
