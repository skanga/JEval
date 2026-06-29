package dev.jeval.optimizer.algorithms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jeval.Golden;
import dev.jeval.optimizer.AcceptedIteration;
import dev.jeval.optimizer.PromptConfiguration;
import dev.jeval.optimizer.policies.TieBreaker;
import dev.jeval.prompt.Prompt;
import dev.jeval.prompt.PromptInterpolationType;
import dev.jeval.prompt.PromptMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GEPAHelperTest {

    @Test
    void equivalentPromptIgnoresOuterWhitespaceForTextPrompts() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);

        assertTrue(gepa.isEquivalentPrompt(
                new Prompt("answer", " Answer {question}\n"),
                new Prompt("answer", "Answer {question}")));
        assertFalse(gepa.isEquivalentPrompt(
                new Prompt("answer", "Answer briefly"),
                new Prompt("answer", "Answer in detail")));
    }

    @Test
    void equivalentPromptComparesMessageRolesAndTrimmedContent() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var original = new Prompt("chat", List.of(
                new PromptMessage("system", "Be direct "),
                new PromptMessage("user", "{question}")),
                PromptInterpolationType.FSTRING);
        var same = new Prompt("chat", List.of(
                new PromptMessage("system", " Be direct"),
                new PromptMessage("user", "{question}\n")),
                PromptInterpolationType.FSTRING);
        var changedRole = new Prompt("chat", List.of(
                new PromptMessage("assistant", "Be direct"),
                new PromptMessage("user", "{question}")),
                PromptInterpolationType.FSTRING);

        assertTrue(gepa.isEquivalentPrompt(original, same));
        assertFalse(gepa.isEquivalentPrompt(original, changedRole));
        assertFalse(gepa.isEquivalentPrompt(original, new Prompt("answer", "Be direct")));
    }

    @Test
    void drawMinibatchSamplesUpToConfiguredSizeWithSeededRandomState() {
        var goldens = List.of(
                Golden.builder("q1").expectedOutput("a1").build(),
                Golden.builder("q2").expectedOutput("a2").build(),
                Golden.builder("q3").expectedOutput("a3").build());
        var first = new GEPA(1, 2, 1, 11, 1, TieBreaker.PREFER_CHILD);
        var second = new GEPA(1, 2, 1, 11, 1, TieBreaker.PREFER_CHILD);

        var firstBatch = first.drawMinibatch(goldens);
        var secondBatch = second.drawMinibatch(goldens);

        assertEquals(2, firstBatch.size());
        assertEquals(firstBatch, secondBatch);
        assertTrue(goldens.containsAll(firstBatch));
    }

    @Test
    void drawMinibatchReturnsAllPossibleSlotsForSmallInputs() {
        var goldens = List.of(Golden.builder("q1").expectedOutput("a1").build());
        var gepa = new GEPA(1, 3, 1, 11, 1, TieBreaker.PREFER_CHILD);

        assertEquals(1, gepa.drawMinibatch(goldens).size());
        assertEquals(List.of(), gepa.drawMinibatch(List.of()));
    }

    @Test
    void childConfigurationCopiesParentPromptsAndReplacesTargetModule() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var answer = new Prompt("answer", "Answer");
        var judge = new Prompt("judge", "Judge");
        var rewritten = new Prompt("answer", "Better answer");
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put("answer", answer);
        prompts.put("judge", judge);
        var parent = PromptConfiguration.create(prompts);

        var child = gepa.childConfiguration(parent, "answer", rewritten);

        assertEquals(parent.id(), child.parent());
        assertEquals(rewritten, child.prompts().get("answer"));
        assertEquals(judge, child.prompts().get("judge"));
        assertEquals(List.of("answer", "judge"), child.prompts().keySet().stream().toList());
    }

    @Test
    void acceptChildUsesParetoNonDominationInsteadOfAverageImprovement() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var parent = PromptConfiguration.create(new LinkedHashMap<>(
                Map.of("answer", new Prompt("answer", "Answer"))));
        var child = gepa.childConfiguration(parent, "answer", new Prompt("answer", "Better answer"));
        var paretoScores = new LinkedHashMap<String, List<Double>>();
        var parents = new LinkedHashMap<String, String>();
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        var accepted = new ArrayList<AcceptedIteration>();
        paretoScores.put(parent.id(), List.of(0.4, 0.6));
        parents.put(parent.id(), null);
        configs.put(parent.id(), parent);

        assertTrue(gepa.acceptChild(
                parent, child, "answer", List.of(0.6, 0.5), List.of(0.7, 0.4),
                paretoScores, parents, configs, accepted));

        assertEquals(List.of(0.7, 0.4), paretoScores.get(child.id()));
        assertEquals(parent.id(), parents.get(child.id()));
        assertEquals(child, configs.get(child.id()));
        assertEquals(List.of(new AcceptedIteration(parent.id(), child.id(), "answer", 0.55, 0.55)), accepted);

        var rejected = gepa.childConfiguration(child, "answer", new Prompt("answer", "Dominated answer"));
        assertFalse(gepa.acceptChild(
                child, rejected, "answer", List.of(0.7, 0.4), List.of(0.6, 0.4),
                paretoScores, parents, configs, accepted));

        assertFalse(paretoScores.containsKey(rejected.id()));
        assertFalse(parents.containsKey(rejected.id()));
        assertFalse(configs.containsKey(rejected.id()));
        assertEquals(1, accepted.size());
    }

    @Test
    void acceptChildPrunesArchiveScoresDominatedByAcceptedChild() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var parent = PromptConfiguration.create(new LinkedHashMap<>(
                Map.of("answer", new Prompt("answer", "Answer"))));
        var weak = PromptConfiguration.create(new LinkedHashMap<>(
                Map.of("answer", new Prompt("answer", "Weak"))));
        var child = gepa.childConfiguration(parent, "answer", new Prompt("answer", "Better answer"));
        var paretoScores = new LinkedHashMap<String, List<Double>>();
        var parents = new LinkedHashMap<String, String>();
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        var accepted = new ArrayList<AcceptedIteration>();
        paretoScores.put(parent.id(), List.of(0.4, 0.4));
        paretoScores.put(weak.id(), List.of(0.3, 0.3));
        parents.put(parent.id(), null);
        parents.put(weak.id(), null);
        configs.put(parent.id(), parent);
        configs.put(weak.id(), weak);

        assertTrue(gepa.acceptChild(
                parent, child, "answer", List.of(0.4, 0.4), List.of(0.5, 0.5),
                paretoScores, parents, configs, accepted));

        assertEquals(List.of(0.5, 0.5), paretoScores.get(child.id()));
        assertFalse(paretoScores.containsKey(parent.id()));
        assertFalse(paretoScores.containsKey(weak.id()));
        assertEquals(parent.id(), parents.get(child.id()));
        assertEquals(child, configs.get(child.id()));
    }

    @Test
    void bestByAggregateChoosesHighestAverageScore() {
        var gepa = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var root = PromptConfiguration.create(new LinkedHashMap<>(
                Map.of("answer", new Prompt("answer", "Root"))));
        var child = gepa.childConfiguration(root, "answer", new Prompt("answer", "Child"));
        var scores = new LinkedHashMap<String, List<Double>>();
        var parents = new LinkedHashMap<String, String>();
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        scores.put(root.id(), List.of(0.4, 0.6));
        scores.put(child.id(), List.of(0.9, 0.7));
        parents.put(root.id(), null);
        parents.put(child.id(), root.id());
        configs.put(root.id(), root);
        configs.put(child.id(), child);

        assertEquals(child, gepa.bestByAggregate(scores, parents, configs));
    }

    @Test
    void bestByAggregateUsesTieBreakerPolicy() {
        var rootFirst = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_ROOT);
        var childFirst = new GEPA(1, 2, 1, 7, 1, TieBreaker.PREFER_CHILD);
        var root = PromptConfiguration.create(new LinkedHashMap<>(
                Map.of("answer", new Prompt("answer", "Root"))));
        var child = childFirst.childConfiguration(root, "answer", new Prompt("answer", "Child"));
        var scores = new LinkedHashMap<String, List<Double>>();
        var parents = new LinkedHashMap<String, String>();
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        scores.put(root.id(), List.of(0.7, 0.7));
        scores.put(child.id(), List.of(0.7, 0.7));
        parents.put(root.id(), null);
        parents.put(child.id(), root.id());
        configs.put(root.id(), root);
        configs.put(child.id(), child);

        assertEquals(root, rootFirst.bestByAggregate(scores, parents, configs));
        assertEquals(child, childFirst.bestByAggregate(scores, parents, configs));
    }
}
