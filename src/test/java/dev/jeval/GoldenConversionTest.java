package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoldenConversionTest {

    @Test
    void goldenConvertsToLlmTestCase() {
        var golden = Golden.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(List.of("context"))
                .retrievalContext(List.of("retrieved"))
                .toolsCalled(List.of(new ToolCall("Search")))
                .expectedTools(List.of(new ToolCall("Lookup")))
                .additionalMetadata(Map.of("source", "unit"))
                .comments("comment")
                .name("golden")
                .customColumnKeyValues(Map.of("case_id", "case-1"))
                .multimodal(true)
                .build();

        var testCase = golden.toTestCase();

        assertAll(
                () -> assertEquals(golden.input(), testCase.input()),
                () -> assertEquals(golden.actualOutput(), testCase.actualOutput()),
                () -> assertEquals(golden.expectedOutput(), testCase.expectedOutput()),
                () -> assertEquals(golden.context(), testCase.context()),
                () -> assertEquals(golden.retrievalContext(), testCase.retrievalContext()),
                () -> assertEquals(golden.toolsCalled(), testCase.toolsCalled()),
                () -> assertEquals(golden.expectedTools(), testCase.expectedTools()),
                () -> assertEquals(golden.additionalMetadata(), testCase.additionalMetadata()),
                () -> assertEquals(golden.comments(), testCase.comments()),
                () -> assertEquals(golden.name(), testCase.name()),
                () -> assertEquals(null, testCase.customColumnKeyValues()),
                () -> assertEquals(false, testCase.multimodal()));
    }

    @Test
    void llmTestCaseConvertsToGolden() {
        var testCase = LlmTestCase.builder("input")
                .actualOutput("actual")
                .expectedOutput("expected")
                .context(List.of("context"))
                .retrievalContext(List.of("retrieved"))
                .toolsCalled(List.of(new ToolCall("Search")))
                .expectedTools(List.of(new ToolCall("Lookup")))
                .additionalMetadata(Map.of("source", "unit"))
                .comments("comment")
                .name("case")
                .customColumnKeyValues(Map.of("case_id", "case-1"))
                .build();

        var golden = Golden.from(testCase);

        assertAll(
                () -> assertEquals(testCase.input(), golden.input()),
                () -> assertEquals(testCase.actualOutput(), golden.actualOutput()),
                () -> assertEquals(testCase.expectedOutput(), golden.expectedOutput()),
                () -> assertEquals(testCase.context(), golden.context()),
                () -> assertEquals(testCase.retrievalContext(), golden.retrievalContext()),
                () -> assertEquals(testCase.toolsCalled(), golden.toolsCalled()),
                () -> assertEquals(testCase.expectedTools(), golden.expectedTools()),
                () -> assertEquals(testCase.additionalMetadata(), golden.additionalMetadata()),
                () -> assertNull(golden.comments()),
                () -> assertNull(golden.name()),
                () -> assertNull(golden.customColumnKeyValues()));
    }

    @Test
    void conversationalGoldenConvertsToConversationalTestCase() {
        var golden = ConversationalGolden.builder("scenario")
                .turns(List.of(new Turn("user", "hello")))
                .expectedOutcome("outcome")
                .userDescription("user")
                .context(List.of("context"))
                .additionalMetadata(Map.of("source", "unit"))
                .multimodal(true)
                .build();

        var testCase = golden.toTestCase();

        assertAll(
                () -> assertEquals(golden.turns(), testCase.turns()),
                () -> assertEquals(golden.scenario(), testCase.scenario()),
                () -> assertEquals(golden.expectedOutcome(), testCase.expectedOutcome()),
                () -> assertEquals(golden.userDescription(), testCase.userDescription()),
                () -> assertEquals(golden.context(), testCase.context()),
                () -> assertEquals(false, testCase.multimodal()));
    }

    @Test
    void conversationalTestCaseConvertsToConversationalGoldenWhenScenarioExists() {
        var testCase = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .scenario("scenario")
                .expectedOutcome("outcome")
                .userDescription("user")
                .context(List.of("context"))
                .comments("comment")
                .name("conversation")
                .build();

        var golden = ConversationalGolden.from(testCase);

        assertAll(
                () -> assertEquals(testCase.turns(), golden.turns()),
                () -> assertEquals(testCase.scenario(), golden.scenario()),
                () -> assertEquals(testCase.expectedOutcome(), golden.expectedOutcome()),
                () -> assertEquals(testCase.userDescription(), golden.userDescription()),
                () -> assertEquals(testCase.context(), golden.context()),
                () -> assertNull(golden.comments()),
                () -> assertNull(golden.name()));
    }

    @Test
    void conversationalTestCaseConversionRequiresScenario() {
        var missingScenario = ConversationalTestCase.builder(List.of(new Turn("user", "hello"))).build();
        var blankScenario = ConversationalTestCase.builder(List.of(new Turn("user", "hello")))
                .scenario("")
                .build();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> ConversationalGolden.from(missingScenario)),
                () -> assertThrows(IllegalArgumentException.class, () -> ConversationalGolden.from(blankScenario)));
    }
}
