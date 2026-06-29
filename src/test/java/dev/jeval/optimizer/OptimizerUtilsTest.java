package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OptimizerUtilsTest {

    @Test
    void buildPromptConfigSnapshotsCopiesStoredPromptConfigurations() {
        var rootPrompt = new Prompt("root", "Root prompt");
        var childPrompt = new Prompt("child", "Child prompt");
        var rootPrompts = new LinkedHashMap<String, Prompt>();
        rootPrompts.put("generator", rootPrompt);
        var childPrompts = new LinkedHashMap<String, Prompt>();
        childPrompts.put("generator", childPrompt);
        var configs = new LinkedHashMap<String, PromptConfiguration>();
        configs.put("root", new PromptConfiguration("root", null, rootPrompts));
        configs.put("child", new PromptConfiguration("child", "root", childPrompts));

        var snapshots = OptimizerUtils.buildPromptConfigSnapshots(configs);

        configs.clear();
        childPrompts.put("judge", new Prompt("judge", "Score"));

        assertEquals(List.of("root", "child"), new ArrayList<>(snapshots.keySet()));
        assertEquals(null, snapshots.get("root").parent());
        assertEquals("root", snapshots.get("child").parent());
        assertSame(rootPrompt, snapshots.get("root").prompts().get("generator"));
        assertSame(childPrompt, snapshots.get("child").prompts().get("generator"));
        assertEquals(List.of("generator"), new ArrayList<>(snapshots.get("child").prompts().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> snapshots.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshots.get("child").prompts().put("other", rootPrompt));
    }

    @Test
    void splitGoldensRejectsNegativeParetoSize() {
        assertThrows(IllegalArgumentException.class,
                () -> OptimizerUtils.splitGoldens(List.of("a"), -1, new Random(1)));
    }

    @Test
    void splitGoldensHandlesEmptyAndSingleItemInputs() {
        var empty = OptimizerUtils.splitGoldens(List.<String>of(), 2, new Random(1));
        var single = OptimizerUtils.splitGoldens(List.of("only"), 2, new Random(1));

        assertEquals(List.of(), empty.feedback());
        assertEquals(List.of(), empty.pareto());
        assertEquals(List.of(), single.feedback());
        assertEquals(List.of("only"), single.pareto());
    }

    @Test
    void splitGoldensSelectsDeterministicallyAndPreservesOriginalOrderWithinSplits() {
        var goldens = List.of("g0", "g1", "g2", "g3", "g4");

        var first = OptimizerUtils.splitGoldens(goldens, 2, new Random(7));
        var second = OptimizerUtils.splitGoldens(goldens, 2, new Random(7));

        assertEquals(first, second);
        assertEquals(3, first.feedback().size());
        assertEquals(2, first.pareto().size());
        assertEquals(new LinkedHashSet<>(goldens), union(first));
        assertEquals(orderByOriginal(goldens, first.feedback()), first.feedback());
        assertEquals(orderByOriginal(goldens, first.pareto()), first.pareto());
    }

    @Test
    void splitGoldensLeavesAtLeastOneFeedbackItemWhenPossible() {
        var split = OptimizerUtils.splitGoldens(List.of("a", "b", "c"), 99, new Random(2));

        assertEquals(1, split.feedback().size());
        assertEquals(2, split.pareto().size());
    }

    private static Set<String> union(OptimizerUtils.GoldenSplit<String> split) {
        var values = new LinkedHashSet<String>();
        values.addAll(split.feedback());
        values.addAll(split.pareto());
        return values;
    }

    private static List<String> orderByOriginal(List<String> original, List<String> values) {
        var selected = new LinkedHashSet<>(values);
        return original.stream().filter(selected::contains).toList();
    }
}
