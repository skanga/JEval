package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.ConversationalGolden;
import dev.jeval.ConversationalMetric;
import dev.jeval.DeepEvalException;
import dev.jeval.Golden;
import dev.jeval.Metric;
import dev.jeval.MetricResult;
import dev.jeval.Turn;
import dev.jeval.optimizer.OptimizerScorer;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MIPROV2BootstrapperTest {

    @Test
    void demonstrationSetRendersExamplesLikeDeepEval() {
        var set = new MIPROV2DemonstrationSet(List.of(
                new MIPROV2Demonstration("What is 2+2?", "4", 0),
                new MIPROV2Demonstration("Capital of France?", "Paris", 1)));

        assertEquals("""
                Here are some examples:

                Example 1:
                Input: What is 2+2?
                Output: 4


                Example 2:
                Input: Capital of France?
                Output: Paris


                Now, please respond to the following:""", set.toText());
    }

    @Test
    void renderPromptWithDemonstrationsReturnsOriginalPromptForEmptySet() {
        var prompt = new Prompt("answer", "Answer {{input}}");

        var rendered = MIPROV2Bootstrapper.renderPromptWithDemonstrations(
                prompt, new MIPROV2DemonstrationSet(List.of()), 8);

        assertSame(prompt, rendered);
    }

    @Test
    void renderPromptWithDemonstrationsPrependsExamplesToTextPrompt() {
        var prompt = new Prompt("answer", "Answer {{input}}");

        var rendered = MIPROV2Bootstrapper.renderPromptWithDemonstrations(prompt, singleDemoSet(), 8);

        assertEquals("""
                Here are some examples:

                Example 1:
                Input: q
                Output: a


                Now, please respond to the following:

                Answer {{input}}""", rendered.textTemplate());
    }

    @Test
    void renderPromptWithDemonstrationsAppendsExamplesToFirstSystemMessage() {
        var prompt = new Prompt("chat", List.of(
                new PromptMessage("system", "Answer carefully."),
                new PromptMessage("user", "{{input}}")), PromptInterpolationType.FSTRING);

        var rendered = MIPROV2Bootstrapper.renderPromptWithDemonstrations(prompt, singleDemoSet(), 8);

        assertEquals(List.of(
                new PromptMessage("system", """
                        Answer carefully.

                        Here are some examples:

                        Example 1:
                        Input: q
                        Output: a


                        Now, please respond to the following:"""),
                new PromptMessage("user", "{{input}}")), rendered.messagesTemplate());
    }

    @Test
    void renderPromptWithDemonstrationsPrependsExamplesToFirstMessageWhenNoSystemMessageExists() {
        var prompt = new Prompt("chat", List.of(
                new PromptMessage("user", "{{input}}"),
                new PromptMessage("assistant", "Prior answer")), PromptInterpolationType.FSTRING);

        var rendered = MIPROV2Bootstrapper.renderPromptWithDemonstrations(prompt, singleDemoSet(), 8);

        assertEquals(List.of(
                new PromptMessage("user", """
                        Here are some examples:

                        Example 1:
                        Input: q
                        Output: a


                        Now, please respond to the following:

                        {{input}}"""),
                new PromptMessage("assistant", "Prior answer")), rendered.messagesTemplate());
    }

    @Test
    void createDemonstrationSetsAlwaysIncludesZeroShotSet() {
        var bootstrapper = new MIPROV2Bootstrapper(1, 1, 2, new Random(1));

        var sets = bootstrapper.createDemonstrationSets(List.of(), List.of());

        assertEquals(1, sets.size());
        assertEquals("0-shot", sets.getFirst().id());
        assertEquals(List.of(), sets.getFirst().demonstrations());
    }

    @Test
    void createDemonstrationSetsCombinesBoundedBootstrappedAndLabeledExamples() {
        var bootstrapper = new MIPROV2Bootstrapper(1, 1, 1, new Random(1));
        var bootstrapped = new MIPROV2Demonstration("boot input", "boot output", 1);
        var labeled = new MIPROV2Demonstration("label input", "label output", 2);

        var sets = bootstrapper.createDemonstrationSets(List.of(bootstrapped), List.of(labeled));

        assertEquals(2, sets.size());
        assertEquals("0-shot", sets.get(0).id());
        assertEquals(2, sets.get(1).demonstrations().size());
        assertEquals(List.of(bootstrapped, labeled), sets.get(1).demonstrations().stream()
                .sorted(java.util.Comparator.comparing(MIPROV2Demonstration::inputText))
                .toList());
    }

    @Test
    void createDemonstrationSetsSkipsLabeledDuplicatesByGoldenIndex() {
        var bootstrapper = new MIPROV2Bootstrapper(1, 1, 1, new Random(1));
        var bootstrapped = new MIPROV2Demonstration("boot input", "boot output", 7);
        var labeled = new MIPROV2Demonstration("label input", "label output", 7);

        var sets = bootstrapper.createDemonstrationSets(List.of(bootstrapped), List.of(labeled));

        assertEquals(2, sets.size());
        assertEquals(List.of(bootstrapped), sets.get(1).demonstrations());
    }

    @Test
    void bootstrapIncludesExpectedOutputAsLabeledDemonstration() {
        var scorer = new OptimizerScorer((prompt, golden) -> "wrong", List.of(failingMetric()));
        var bootstrapper = new MIPROV2Bootstrapper(scorer, 1, 1, 1, new Random(1));

        var sets = bootstrapper.bootstrap(new Prompt("answer", "Answer"), List.of(
                Golden.builder("q").expectedOutput("expected").build()));

        assertEquals("0-shot", sets.get(0).id());
        assertTrue(sets.stream().flatMap(set -> set.demonstrations().stream())
                .anyMatch(demo -> demo.inputText().equals("q")
                        && demo.outputText().equals("expected")
                        && demo.goldenIndex() == 0));
    }

    @Test
    void bootstrapKeepsGeneratedOutputOnlyWhenMetricsSucceed() {
        var scorer = new OptimizerScorer(
                (prompt, golden) -> ((Golden) golden).input().equals("q1") ? "a1" : "wrong",
                List.of(actualEqualsExpectedMetric()));
        var bootstrapper = new MIPROV2Bootstrapper(scorer, 2, 0, 1, new Random(1));

        var sets = bootstrapper.bootstrap(new Prompt("answer", "Answer"), List.of(
                Golden.builder("q1").expectedOutput("a1").build(),
                Golden.builder("q2").expectedOutput("a2").build()));

        assertTrue(sets.stream().flatMap(set -> set.demonstrations().stream())
                .anyMatch(demo -> demo.inputText().equals("q1") && demo.outputText().equals("a1")));
        assertTrue(sets.stream().flatMap(set -> set.demonstrations().stream())
                .noneMatch(demo -> demo.inputText().equals("q2") && demo.outputText().equals("wrong")));
    }

    @Test
    void bootstrapExtractsConversationalInputFromUserTurns() {
        var scorer = new OptimizerScorer(
                (prompt, golden) -> "done",
                List.of((ConversationalMetric) testCase -> new MetricResult("ok", 1.0, 0.5, true, null)));
        var bootstrapper = new MIPROV2Bootstrapper(scorer, 1, 0, 1, new Random(1));

        var sets = bootstrapper.bootstrap(new Prompt("answer", "Answer"), List.of(
                ConversationalGolden.builder("support")
                        .turns(List.of(
                                new Turn("user", "hello"),
                                new Turn("assistant", "hi"),
                                new Turn("user", "need help")))
                        .expectedOutcome("done")
                        .build()));

        assertTrue(sets.stream().flatMap(set -> set.demonstrations().stream())
                .anyMatch(demo -> demo.inputText().equals("hello\nneed help")
                        && demo.outputText().equals("done")));
    }

    @Test
    void bootstrapRejectsGoldensWithoutExpectedOutputWhenNoGeneratedDemoSucceeds() {
        var scorer = new OptimizerScorer((prompt, golden) -> "wrong", List.of(failingMetric()));
        var bootstrapper = new MIPROV2Bootstrapper(scorer, 1, 1, 1, new Random(1));

        var exception = assertThrows(DeepEvalException.class,
                () -> bootstrapper.bootstrap(new Prompt("answer", "Answer"), List.of(Golden.builder("q").build())));

        assertTrue(exception.getMessage().contains("expected_output"));
    }

    private static MIPROV2DemonstrationSet singleDemoSet() {
        return new MIPROV2DemonstrationSet(List.of(new MIPROV2Demonstration("q", "a", 3)));
    }

    private static Metric actualEqualsExpectedMetric() {
        return testCase -> {
            var success = testCase.expectedOutput().equals(testCase.actualOutput());
            return new MetricResult("exact", success ? 1.0 : 0.0, 0.5, success, null);
        };
    }

    private static Metric failingMetric() {
        return ignored -> new MetricResult("fail", 0.0, 0.5, false, null);
    }
}
