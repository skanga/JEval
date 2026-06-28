# CLI Parity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Port the useful DeepEval CLI settings/provider/generate behavior and make JEval's test runner handle real dataset files, not only inline JSON specs.

**Architecture:** Keep the existing manual CLI parser. Add small CLI helper classes for dotenv/settings persistence and generation dispatch. Reuse `EvaluationDataset`, `TestRunner`, `Synthesizer`, and report/store writers instead of adding a CLI framework or provider dependencies.

**Tech Stack:** Java 25, Jackson, JUnit 6, existing JEval types.

---

### Task 1: Settings And Provider Persistence

**Files:**
- Create: `src/main/java/dev/jeval/cli/DotenvFile.java`
- Create: `src/main/java/dev/jeval/cli/CliSettings.java`
- Modify: `src/main/java/dev/jeval/cli/JEvalCli.java`
- Test: `src/test/java/dev/jeval/cli/JEvalCliTest.java`

**Steps:**
1. Write failing tests for `settings -u`, `settings -U`, `settings -l`, `set-debug`, `set-openai`, `unset-openai`, `set-ollama`, `unset-ollama`.
2. Run focused CLI tests and confirm failures.
3. Implement dotenv read/write/update/remove with no duplicate keys.
4. Implement provider command maps for common LLM and embedding providers.
5. Run focused CLI tests and confirm pass.

### Task 2: Generate CLI

**Files:**
- Create: `src/main/java/dev/jeval/cli/GenerateCommand.java`
- Modify: `src/main/java/dev/jeval/cli/JEvalCli.java`
- Test: `src/test/java/dev/jeval/cli/JEvalCliTest.java`

**Steps:**
1. Write failing tests for `generate --method contexts`, `generate --method scratch`, and required-input errors.
2. Use `--responses-file` as the minimal CLI model input because provider modules are application dependencies.
3. Save generated single-turn goldens through `EvaluationDataset`.
4. Run focused CLI tests and confirm pass.

### Task 3: Test Runner Dataset Inputs

**Files:**
- Modify: `src/main/java/dev/jeval/runner/TestRunner.java`
- Test: `src/test/java/dev/jeval/runner/TestRunnerTest.java`

**Steps:**
1. Write failing tests for JSONL and CSV test-case files plus spec files with `"dataset"`.
2. Reuse `EvaluationDataset` import paths for dataset file parsing.
3. Keep metrics limited to deterministic `exact_match` and `pattern_match`.
4. Run focused runner tests and confirm pass.

### Task 4: Verification

**Files:**
- Modify: `README.md`

**Steps:**
1. Add short docs for settings/provider/generate/test-runner inputs.
2. Run focused tests.
3. Run `mvn -q test`.
4. Run `mvn -q clean package`.
5. Commit and push feature branch.
