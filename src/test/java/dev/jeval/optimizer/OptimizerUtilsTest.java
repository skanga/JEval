package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
}
