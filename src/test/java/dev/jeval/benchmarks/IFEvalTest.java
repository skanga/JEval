package dev.jeval.benchmarks;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class IFEvalTest {

    @Test
    void evaluatesInstructionFollowingAndBreakdownLikeDeepEval() {
        var benchmark = new IFEval(List.of(
                Golden.builder("Answer in three words, include safe, and do not use commas.")
                        .metadata(Map.of(
                                "instruction_ids", List.of(
                                        "punctuation:no_comma",
                                        "length_constraints:number_words",
                                        "keywords:must_include"),
                                "kwargs_list", List.of(
                                        Map.of(),
                                        Map.of("num_words", 3, "relation", "exactly"),
                                        Map.of("keywords", List.of("safe")))))
                        .build(),
                Golden.builder("Return JSON without forbidden words.")
                        .metadata(Map.of(
                                "instruction_ids", List.of(
                                        "detectable_format:json",
                                        "detectable_content:forbidden_words"),
                                "kwargs_list", List.of(
                                        Map.of(),
                                        Map.of("forbidden_words", List.of("biased")))))
                        .build()));
        var model = new ScriptedModel("safe calm answer", "{\"answer\":\"biased\"}");

        var result = benchmark.evaluate(model);

        assertAll(
                () -> assertEquals(0.5, result.overallAccuracy()),
                () -> assertEquals(0.5, benchmark.overallScore()),
                () -> assertTrue(benchmark.predictions().getFirst().allInstructionsCorrect()),
                () -> assertFalse(benchmark.predictions().get(1).allInstructionsCorrect()),
                () -> assertEquals(1.0, benchmark.instructionBreakdown().get("punctuation:no_comma")),
                () -> assertEquals(1.0, benchmark.instructionBreakdown().get("detectable_format:json")),
                () -> assertEquals(0.0, benchmark.instructionBreakdown().get("detectable_content:forbidden_words")));
    }

    @Test
    void verifierCoversCoreRuleFamilies() {
        assertAll(
                () -> assertTrue(IFEval.verifyInstruction("ONE TWO", "change_case:english_uppercase", Map.of())),
                () -> assertTrue(IFEval.verifyInstruction("Hello world", "startend:start_with",
                        Map.of("start_text", "Hello"))),
                () -> assertTrue(IFEval.verifyInstruction("- one\n- two", "detectable_format:number_bullets",
                        Map.of("num_bullets", 2))),
                () -> assertTrue(IFEval.verifyInstruction("first\n\nsecond",
                        "structural_constraints:number_paragraphs", Map.of("num_paragraphs", 2))),
                () -> assertTrue(IFEval.verifyInstruction("repeat this", "combination:repeat_prompt",
                        Map.of("prompt_to_repeat", "repeat"))));
    }

    @Test
    void verifierAllowsMissingCountKwargsLikeDeepEval() {
        assertAll(
                () -> assertTrue(IFEval.verifyInstruction("two words",
                        "length_constraints:number_words", Map.of())),
                () -> assertTrue(IFEval.verifyInstruction("one\n\ntwo",
                        "structural_constraints:number_paragraphs", Map.of())),
                () -> assertTrue(IFEval.verifyInstruction("keyword keyword",
                        "detectable_content:keyword_frequency", Map.of())));
    }

    @Test
    void constructorRejectsEmptyGoldens() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> new IFEval(List.of()));

        assertTrue(thrown.getMessage().contains("goldens"));
    }

    private static final class ScriptedModel implements EvaluationModel {
        private final Queue<String> responses;

        private ScriptedModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(String prompt) {
            return responses.remove();
        }
    }
}
