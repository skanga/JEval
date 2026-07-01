package dev.jeval.prompt;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromptDtoTest {

    @Test
    void promptVersionKeepsDeepEvalFields() {
        var version = new PromptVersion("version-1", "v1");

        assertEquals("version-1", version.id());
        assertEquals("v1", version.version());
    }

    @Test
    void promptCommitKeepsDeepEvalFields() {
        var commit = new PromptCommit("commit-1", "abc123", "Initial prompt");

        assertEquals("commit-1", commit.id());
        assertEquals("abc123", commit.hash());
        assertEquals("Initial prompt", commit.message());
    }

    @Test
    void responseListsAreCopied() {
        var commits = new ArrayList<>(List.of(new PromptCommit("commit-1", "abc123", "Initial prompt")));
        var versions = new ArrayList<>(List.of(new PromptVersion("version-1", "v1")));
        var branches = new ArrayList<>(List.of(new PromptBranch("branch-1", "main")));

        var commitsResponse = new PromptCommitsHttpResponse(commits);
        var versionsResponse = new PromptVersionsHttpResponse(versions, versions);
        var branchesResponse = new PromptBranchesHttpResponse(branches);

        commits.add(new PromptCommit("commit-2", "def456", "Update prompt"));
        versions.add(new PromptVersion("version-2", "v2"));
        branches.add(new PromptBranch("branch-2", "experiment"));

        assertEquals(1, commitsResponse.commits().size());
        assertEquals(1, versionsResponse.textVersions().size());
        assertEquals(1, versionsResponse.messagesVersions().size());
        assertEquals(1, branchesResponse.branches().size());
        assertThrows(UnsupportedOperationException.class,
                () -> commitsResponse.commits().add(new PromptCommit("commit-3", "ghi789", "Nope")));
    }

    @Test
    void promptCreateRequestsKeepDeepEvalFields() {
        assertEquals("abc123", new PromptCreateVersion("abc123").hash());
        assertEquals("experiment", new PromptCreateBranchRequest("experiment").branch());
        assertEquals("renamed", new PromptUpdateBranchRequest("renamed").name());
    }

    @Test
    void promptCreateRequestsRejectMissingFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCreateVersion(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCreateVersion(" ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCreateBranchRequest(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCreateBranchRequest(" ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptUpdateBranchRequest(null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptUpdateBranchRequest(" ")));
    }

    @Test
    void promptHttpResponseKeepsDeepEvalFieldsAndCopiesLists() {
        var messages = new ArrayList<>(List.of(new PromptMessage("user", "Hello {{name}}")));
        var tools = new ArrayList<>(List.of(new Tool("tool-1", "search", "Search docs", ToolMode.STRICT, null)));
        var settings = new ModelSettings(ModelProvider.OPEN_AI, "gpt-4", 0.0, 100, null, null, null, null, null, null);
        var schema = new OutputSchema("schema-1", List.of(), "Answer");

        var response = new PromptHttpResponse(
                "prompt-1",
                "abc123",
                "v1",
                "Production",
                null,
                messages,
                PromptInterpolationType.MUSTACHE,
                PromptType.LIST,
                settings,
                OutputType.JSON,
                schema,
                tools,
                "main");

        messages.add(new PromptMessage("assistant", "ignored"));
        tools.add(new Tool("tool-2", "lookup", "Lookup docs", ToolMode.ALLOW_ADDITIONAL, null));

        assertEquals("prompt-1", response.id());
        assertEquals("abc123", response.hash());
        assertEquals("v1", response.version());
        assertEquals("Production", response.label());
        assertEquals(1, response.messages().size());
        assertEquals(PromptInterpolationType.MUSTACHE, response.interpolationType());
        assertEquals(PromptType.LIST, response.type());
        assertEquals(settings, response.modelSettings());
        assertEquals(OutputType.JSON, response.outputType());
        assertEquals(schema, response.outputSchema());
        assertEquals(1, response.tools().size());
        assertEquals("main", response.branch());
        assertThrows(UnsupportedOperationException.class,
                () -> response.messages().add(new PromptMessage("user", "Nope")));
    }

    @Test
    void promptPushRequestKeepsDeepEvalFieldsAndCopiesLists() {
        var messages = new ArrayList<>(List.of(new PromptMessage("user", "Hello")));
        var tools = new ArrayList<>(List.of(new Tool("tool-1", "search", "Search docs", ToolMode.STRICT, null)));

        var request = new PromptPushRequest(
                "support-agent",
                null,
                messages,
                tools,
                PromptInterpolationType.FSTRING,
                null,
                null,
                OutputType.TEXT,
                "experiment");

        messages.add(new PromptMessage("assistant", "ignored"));
        tools.add(new Tool("tool-2", "lookup", "Lookup docs", ToolMode.ALLOW_ADDITIONAL, null));

        assertEquals("support-agent", request.alias());
        assertEquals(1, request.messages().size());
        assertEquals(1, request.tools().size());
        assertEquals(PromptInterpolationType.FSTRING, request.interpolationType());
        assertEquals(OutputType.TEXT, request.outputType());
        assertEquals("experiment", request.branch());
        assertThrows(UnsupportedOperationException.class,
                () -> request.tools().add(new Tool("tool-3", "nope", "Nope", ToolMode.STRICT, null)));
    }

    @Test
    void promptPushRequestRejectsInvalidPayloadShape() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptPushRequest(null, "text", null, null, null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptPushRequest(" ", "text", null, null, null, null, null, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptPushRequest(
                                "support-agent",
                                "text",
                                List.of(new PromptMessage("user", "Hello")),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));
    }

    @Test
    void promptApiKeepsDeepEvalFields() {
        var prompt = new PromptApi("prompt-1", PromptType.TEXT);

        assertEquals("prompt-1", prompt.id());
        assertEquals(PromptType.TEXT, prompt.type());
    }

    @Test
    void promptResponseRecordsRejectMissingFields() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptVersion(null, "v1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptVersion("version-1", " ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCommit(null, "abc123", "Initial prompt")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptCommit("commit-1", " ", "Initial prompt")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptBranch(null, "main")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptBranch("branch-1", " ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptApi(null, PromptType.TEXT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PromptApi("prompt-1", null)));
    }
}
