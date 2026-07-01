package dev.jeval.synthesizer;

import com.fasterxml.jackson.annotation.JsonAlias;
import dev.jeval.Turn;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jeval.metrics.MetricUtils;
import java.util.List;

final class SynthesizerSchemas {
    private static final ObjectMapper JSON = new ObjectMapper();

    private SynthesizerSchemas() {
    }

    static List<SyntheticData> parseSyntheticData(String json) {
        try {
            return parse(json, SyntheticDataList.class).data();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic data JSON", error);
        }
    }

    static String parseRewrittenInput(String json) {
        try {
            return parse(json, RewrittenInput.class).rewrittenInput();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse rewritten input JSON", error);
        }
    }

    static String parseInput(String json) {
        try {
            return parse(json, SyntheticInput.class).input();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic input JSON", error);
        }
    }

    static String parseSql(String json) {
        try {
            return parse(json, SqlData.class).sql();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse SQL data JSON", error);
        }
    }

    static StylingConfig parseStylingConfig(String json) {
        try {
            var styling = parse(json, PromptStyling.class);
            return new StylingConfig(styling.scenario(), styling.task(), styling.inputFormat(), null);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse prompt styling JSON", error);
        }
    }

    static ConversationalStylingConfig parseConversationalStylingConfig(String json) {
        try {
            var styling = parse(json, ConversationalPromptStyling.class);
            return new ConversationalStylingConfig(
                    styling.scenarioContext(), styling.conversationalTask(), styling.participantRoles(), null);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse conversational prompt styling JSON", error);
        }
    }

    static InputFeedback parseInputFeedback(String json) {
        try {
            return parse(json, InputFeedback.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse input feedback JSON", error);
        }
    }

    static ScenarioFeedback parseScenarioFeedback(String json) {
        try {
            return parse(json, ScenarioFeedback.class);
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse scenario feedback JSON", error);
        }
    }

    static String parseRewrittenScenario(String json) {
        try {
            return parse(json, RewrittenScenario.class).rewrittenScenario();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse rewritten scenario JSON", error);
        }
    }

    static String parseScenario(String json) {
        try {
            return parse(json, SyntheticScenario.class).scenario();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse synthetic scenario JSON", error);
        }
    }

    static List<ConversationalData> parseConversationalData(String json) {
        try {
            return parse(json, ConversationalDataList.class).data();
        } catch (Exception error) {
            throw new IllegalArgumentException("Unable to parse conversational synthetic data JSON", error);
        }
    }

    private static <T> T parse(String json, Class<T> type) throws com.fasterxml.jackson.core.JsonProcessingException {
        return JSON.treeToValue(MetricUtils.trimAndLoadJson(json), type);
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

    private record SqlData(String sql) {
    }

    private record PromptStyling(
            String scenario,
            String task,
            @JsonAlias("input_format") String inputFormat) {
    }

    private record ConversationalPromptStyling(
            @JsonAlias("scenario_context") String scenarioContext,
            @JsonAlias("conversational_task") String conversationalTask,
            @JsonAlias("participant_roles") String participantRoles) {
    }

    record InputFeedback(String feedback, double score) {
        InputFeedback {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("Input feedback score must be finite");
            }
        }
    }

    record ScenarioFeedback(String feedback, double score) {
        ScenarioFeedback {
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("Scenario feedback score must be finite");
            }
        }
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
