# Synthesizer V1 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a minimal DeepEval-style single-turn synthesizer that can generate `Golden` objects from contexts, scratch prompts, and existing goldens.

**Architecture:** Keep the synthesizer as a small adapter over JEval's existing `EvaluationModel` and `Golden` types. It sends JSON-only prompts, parses JSON with Jackson, and returns immutable `Golden` records without adding document chunking, embeddings, async duplicates, or conversational support.

**Tech Stack:** Java 25, Jackson, JUnit 6, existing `EvaluationModel`, existing `Golden`.

---

### Task 1: Prompt Templates And JSON Schemas

**Files:**
- Create: `src/main/java/dev/jeval/synthesizer/SynthesizerPrompts.java`
- Create: `src/main/java/dev/jeval/synthesizer/SynthesizerSchemas.java`
- Test: `src/test/java/dev/jeval/synthesizer/SynthesizerSchemasTest.java`

**Steps:**
1. Write failing tests for parsing `{"data":[{"input":"...","expected_output":"..."}]}` and prompt content for context generation.
2. Run `mvn -q '-Dtest=SynthesizerSchemasTest' test` and confirm compile failure.
3. Implement package-private prompt/schema helpers with only the JSON shapes v1 needs.
4. Rerun the focused test and confirm pass.

### Task 2: Single-Turn Synthesizer API

**Files:**
- Create: `src/main/java/dev/jeval/synthesizer/Synthesizer.java`
- Test: `src/test/java/dev/jeval/synthesizer/SynthesizerTest.java`

**Steps:**
1. Write failing tests for `generateGoldensFromContexts`, `generateGoldensFromScratch`, and `generateGoldensFromGoldens`.
2. Run `mvn -q '-Dtest=SynthesizerTest' test` and confirm missing class/method failure.
3. Implement the minimal API using `EvaluationModel.generate(...)`, `Golden.builder(...)`, and metadata key `evolutions`.
4. Rerun focused tests and confirm pass.

### Task 3: CLI Generate Command

**Files:**
- Modify: `src/main/java/dev/jeval/cli/JEvalCli.java`
- Test: `src/test/java/dev/jeval/cli/JEvalCliTest.java`
- Modify: `README.md`

**Steps:**
1. Write failing CLI tests for unsupported `generate` without provider/model wiring, returning a clear error that library API should be used.
2. Run `mvn -q '-Dtest=JEvalCliTest' test` and confirm failure.
3. Add only the guard/error text. Do not invent provider settings or network model construction here.
4. Update README with the Java API example.
5. Rerun focused tests and confirm pass.

### Task 4: Verification

**Files:**
- No new source files.

**Steps:**
1. Run `mvn -q '-Dtest=SynthesizerSchemasTest,SynthesizerTest,JEvalCliTest' test`.
2. Run `mvn -q test`.
3. Run `mvn -q clean package`.
4. Review `git diff --stat` and commit.
