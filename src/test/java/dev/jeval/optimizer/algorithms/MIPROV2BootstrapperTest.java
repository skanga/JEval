package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    private static MIPROV2DemonstrationSet singleDemoSet() {
        return new MIPROV2DemonstrationSet(List.of(new MIPROV2Demonstration("q", "a", 3)));
    }
}
