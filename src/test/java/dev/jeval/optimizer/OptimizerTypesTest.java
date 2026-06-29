package dev.jeval.optimizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.jeval.prompt.Prompt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OptimizerTypesTest {

    @Test
    void promptConfigurationCreateCopiesPromptsAndGeneratesUuid() {
        var prompt = new Prompt("answer", "Answer: {question}");
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put("generator", prompt);

        var config = PromptConfiguration.create(prompts, "root");

        UUID.fromString(config.id());
        assertEquals("root", config.parent());
        assertSame(prompt, config.prompts().get("generator"));

        prompts.put("judge", new Prompt("judge", "Score it"));

        assertEquals(Set.of("generator"), config.prompts().keySet());
        assertThrows(UnsupportedOperationException.class,
                () -> config.prompts().put("other", new Prompt("other", "Other")));
    }

    @Test
    void runnerStatusTypeExposesDeepEvalWireValues() {
        assertEquals("progress", RunnerStatusType.PROGRESS.value());
        assertEquals("tie", RunnerStatusType.TIE.value());
        assertEquals("error", RunnerStatusType.ERROR.value());
    }

    @Test
    void iterationLogEntryAllowsMissingBeforeAndAfterScores() {
        var entry = new IterationLogEntry(2, "skipped", "no improvement", 0.25, null, null);

        assertEquals(2, entry.iteration());
        assertEquals("skipped", entry.outcome());
        assertEquals("no improvement", entry.reason());
        assertEquals(0.25, entry.elapsed());
        assertEquals(null, entry.before());
        assertEquals(null, entry.after());
    }

    @Test
    void optimizationReportCopiesNestedCollections() {
        var accepted = new ArrayList<>(List.of(new AcceptedIteration("root", "child", "generator", 0.4, 0.7)));
        var paretoScores = new LinkedHashMap<String, List<Double>>();
        paretoScores.put("child", new ArrayList<>(List.of(0.7, 0.8)));
        var parents = new LinkedHashMap<String, String>();
        parents.put("root", null);
        parents.put("child", "root");
        var prompts = new LinkedHashMap<String, Prompt>();
        prompts.put("generator", new Prompt("answer", "Answer"));
        var snapshots = new LinkedHashMap<String, PromptConfigSnapshot>();
        snapshots.put("child", new PromptConfigSnapshot("root", prompts));

        var report = new OptimizationReport("opt-1", "child", accepted, paretoScores, parents, snapshots);

        accepted.add(new AcceptedIteration("child", "grandchild", "generator", 0.7, 0.9));
        paretoScores.get("child").add(0.9);
        parents.put("grandchild", "child");
        prompts.put("judge", new Prompt("judge", "Score"));

        assertEquals(1, report.acceptedIterations().size());
        assertEquals(List.of(0.7, 0.8), report.paretoScores().get("child"));
        assertEquals(Set.of("root", "child"), report.parents().keySet());
        assertEquals(Set.of("generator"), report.promptConfigurations().get("child").prompts().keySet());
        assertThrows(UnsupportedOperationException.class, () -> report.acceptedIterations().clear());
        assertThrows(UnsupportedOperationException.class, () -> report.paretoScores().get("child").add(1.0));
        assertThrows(UnsupportedOperationException.class, () -> report.parents().put("other", "root"));
        assertThrows(UnsupportedOperationException.class,
                () -> report.promptConfigurations().get("child").prompts().clear());
    }
}
