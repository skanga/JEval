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
