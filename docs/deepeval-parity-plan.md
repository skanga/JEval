# DeepEval Parity Plan

Source snapshot: `confident-ai/deepeval@8ebfa33d78db4cf81c0ae340b1a925e5406469c8`.

Port order:

1. Core test model and evaluator: `LLMTestCase`, `ToolCall`, `SingleTurnParams`, metric result/state, `evaluate`, `assert_test`.
2. Deterministic local metrics first: `ExactMatchMetric`, `PatternMatchMetric`, JSON correctness helpers.
3. Single-turn LLM metrics: answer relevancy, faithfulness, contextual precision/recall/relevancy, hallucination, toxicity, bias, summarization.
4. Tool and agent metrics: tool correctness/use, task completion, MCP use, plan quality/adherence, step efficiency.
5. Conversational test cases and turn metrics.
6. Dataset, test-run, tracing, and local report storage.
7. Model provider layer only where metrics need it. Keep providers behind a tiny interface; add SDK dependencies only when a ported metric test requires one.
8. Benchmarks and synthesizer after evaluation behavior is stable.

Rule for each slice: port the upstream test conditions first, watch the Java test fail, then add the smallest Java code that passes.

Current Java parity:

- `LlmTestCase` with optional fields, generated identifier, dataset metadata, metadata, comments, cost/time, tags, custom columns, MCP server/call/resource/prompt fields, tool calls, runtime context/tag/tool-call/custom-column/MCP map validation, multimodal placeholder detection, and registered image mapping
- DeepEval-style `metadata` aliases for single-turn test cases
- `ConversationalTestCase` and `Turn` with optional fields, conversational dataset metadata, MCP server and turn call/resource/prompt fields, DeepEval-style MCP interaction detection, runtime context/RetrievedContextData/tag/tool-call/MCP map validation, multimodal placeholder detection, and registered image mapping
- DeepEval-style `additionalMetadata` aliases for conversational test cases and turns
- `Golden` and `ConversationalGolden` with optional fields, DeepEval-style metadata aliases, dataset metadata, runtime context/tool-call/custom-column validation, multimodal placeholder detection, and registered image mapping
- Golden/test-case conversion helpers, including dataset-rank indexing when datasets materialize goldens as test cases
- API test-case DTO conversion for single-turn and conversational test cases, including dataset-rank order precedence
- `MllmImage` local/remote/Base64 image placeholder support, placeholder parsing, and Windows-safe remote URL handling
- `RetrievedContextData` value type with DeepEval-style string serialization, source-preserving dataset markers, and single-turn, multi-turn, and golden retrieval-context builder support
- `Contestant` and `ArenaTestCase` with DeepEval-style unique-name, shared-input, shared-expected-output, and multimodal propagation rules
- `McpToolCall`, `McpPromptCall`, `McpResourceCall`, and `McpServer` value types from DeepEval `mcp.py`, including null-valued MCP tool args
- `ToolCall` required name validation, string-keyed/null-valued input parameters, equality/hash behavior, and DeepEval-style populated-field string representation
- `ToolCallParam`
- `MultiTurnParam`
- `SingleTurnParam`
- Core `Utils` and `Scorer` dependency-free helpers for text shortening/normalization, missing checks, camel-to-snake conversion, deterministic sorted serialization, trace-safe JSON serialization for records/maps/iterables/arrays with DeepEval-style repeated-reference detection, key-preserving nested null-byte cleanup, LCS, DeepEval-default word chunking and batching, exact/quasi matching, truth-identification scoring, and pass@k scoring
- Prompt interpolation type enum and dispatch helpers for mustache, spaced mustache, f-string, dollar-bracket, and lenient Jinja placeholders with DeepEval-style missing-variable errors and JSON-brace preservation
- `PromptType` enum and prompt type tracking for text versus message-list prompts
- Prompt API value enums for model settings, tool mode, output type, and schema data type
- `ModelSettings` prompt API value record
- `OutputSchemaField` and `OutputSchema` prompt API value records
- `Tool` prompt API value type with generated ids
- Prompt version, commit, and branch API value/response records
- Prompt HTTP response and push request API value records
- `Prompt` and `PromptMessage` manual construction plus file loading/interpolation for `.txt` templates and JSON message lists/dictionaries, including DeepEval-style loaded-template return values and text fallback for invalid JSON/message shapes
- `Prompt` constructor storage for model settings, output type/schema, API key, and branch
- `Evaluator.evaluate(...)` for one test case and a list of test cases
- `Evaluator.evaluate(...)` for one conversational test case and `evaluateConversations(...)` for lists
- `Evaluator.assertTest(...)` for single-turn and conversational test cases
- `EvaluationDataset` with single-turn/multi-turn separation, golden storage/conversion, and evaluation delegation for test cases and goldens
- `EvaluationDataset` construction from existing single-turn or conversational goldens
- `EvaluationDataset.addTestCasesFromJsonFile(...)` for single-turn JSON imports, including tool calls and metadata
- `EvaluationDataset.addTestCasesFromJsonFile(...)` for single-turn JSON imports, including MCP server/call/resource/prompt fields
- JSON object-list imports accept arrays or JSON strings for MCP server/call/resource/prompt fields
- `EvaluationDataset.addTestCasesFromCsvFile(...)` for single-turn CSV imports, including DeepEval `;` context delimiters, custom context delimiters, empty-list context/tool defaults, tool calls, metadata, and dataset-rank indexing through add-test-case paths
- `EvaluationDataset.addGoldensFromJsonFile(...)` for single-turn JSON imports, including tool calls and metadata
- `EvaluationDataset.addGoldensFromJsonFile(...)` custom key names for single-turn JSON fields, tools, metadata, comments, source file, and custom columns
- `EvaluationDataset.addGoldensFromJsonFile(...)` custom key names for conversational JSON scenario, turns, outcome, user description, context, metadata, comments, name, and custom columns
- `EvaluationDataset.saveAsJsonFile(...)` for single-turn JSON exports, including tool calls, golden metadata, and optional test-case inclusion
- `EvaluationDataset` JSON/JSONL/CSV save methods create missing parent directories
- `EvaluationDataset.saveAs(...)` generic format dispatcher for JSON, JSONL, and CSV with DeepEval-style goldens-only default and optional file names
- `EvaluationDataset.addGoldensFromJsonFile(...)` and `saveAsJsonFile(...)` for conversational JSON with structured turns
- Structured turn JSON imports/exports use DeepEval's `metadata` key while still reading older `additional_metadata`
- `EvaluationDataset.addGoldensFromJsonlFile(...)` and `saveAsJsonlFile(...)` for single-turn and conversational JSONL, including tool lists as arrays or JSON strings and optional test-case inclusion
- `EvaluationDataset.addGoldensFromJsonlFile(...)` custom key names for single-turn and conversational JSONL
- Golden JSON/JSONL context and retrieval-context imports accept arrays or delimited strings, with custom JSONL delimiters
- `EvaluationDataset.addGoldensFromCsvFile(...)` and `saveAsCsvFile(...)` for single-turn and conversational CSV goldens, including empty-list context/tool defaults and optional test-case inclusion
- `EvaluationDataset.addGoldensFromCsvFile(...)` custom column names, context delimiters, and tool delimiters for single-turn CSV fields, tools as JSON or delimited names, comments, source file, metadata, and custom columns
- `EvaluationDataset.addGoldensFromCsvFile(...)` custom column names for conversational CSV scenario, turns, outcome, user description, comments, name, context, metadata, and custom columns
- Single-turn synthesizer for contexts, docs, scratch styling, and existing goldens, with CLI generation and OpenAI/Ollama LangChain4j provider wiring
- Multi-turn synthesizer for contexts, docs, scratch styling, and existing conversational goldens, producing `ConversationalGolden` values with scenario, turns, expected outcome, context, and metadata
- BoolQ benchmark evaluation over supplied `Golden` rows with DeepEval-style prediction rows and `overallScore`
- ARC benchmark evaluation over supplied `Golden` rows with DeepEval-style prediction rows, `ARCMode`, and `overallScore`
- MMLU benchmark evaluation over task-grouped supplied `Golden` rows with task prediction rows, task scores, and `overallScore`
- HellaSwag benchmark evaluation over task-grouped supplied `Golden` rows with task prediction rows, task scores, batch support, and `overallScore`
- `EvaluationModel.batchGenerate(...)` fallback and MMLU batch-size evaluation with DeepEval-style response count validation
- `ExactMatchMetric` with DeepEval-required input, actual output, and expected output validation
- `JsonCorrectnessMetric` deterministic schema-validation path with DeepEval-required input and actual output validation, including local success/failure reasons when enabled
- `PatternMatchMetric` with DeepEval-required input and actual output validation
- `ToolCorrectnessMetric` deterministic scoring path with DeepEval-required input/tools validation, available-tool selection score capping, and strict-mode score zeroing
- Metric utility validation for selected single-turn and conversational required params
- Metric arena utility validation for selected contestant single-turn params
- Metric turn-to-dictionary utility for selected turn fields
- Metric turn sliding-window and unit-interaction utilities
- Metric single-turn formatting utility for selected non-empty fields
- Metric JSON trimming/loading utility with trailing-comma repair
- GEval utility formatting and upload payload helpers for selected single-turn and conversational params
- GEval criteria/evaluation-step validation and rubric sorting/formatting helpers
- GEval pull-param mapping, param label joining, numbering, and score-range helpers
- GEval upload payload support for evaluation steps, rubrics, and unsupported-param rejection
- GEval metric pull response and API rubric value records
- Answer relevancy schema value records
- Answer relevancy schema extraction from model JSON for statements, verdicts, and reasons
- Answer relevancy metric required-param validation and verdict scoring shell
- Answer relevancy prompt construction for statements, verdicts, reason, and multimodal rules
- Tiny evaluation model interface and model-backed answer relevancy prompt/parse execution path
- Faithfulness schema value records and model JSON extraction for truths, claims, verdicts, and reasons
- Faithfulness metric required-param validation, verdict scoring, strict mode, and ambiguous-claim penalty shell
- Faithfulness prompt construction for truths, claims, verdicts, reason, extraction limits, and multimodal wording
- Model-backed faithfulness prompt/parse execution path through the tiny evaluation model interface
- Contextual relevancy schema value records and model JSON extraction for verdicts and reasons
- Contextual relevancy metric required-param validation, per-context verdict storage, scoring, and strict mode shell
- Contextual relevancy prompt construction for verdicts, reasons, text context, and multimodal context wording
- Model-backed contextual relevancy prompt/parse execution path through the tiny evaluation model interface
- Contextual recall schema records, required-param validation, verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Contextual precision schema records, required-param validation, ranked average-precision scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Hallucination schema records, required-param validation, lower-is-better contradiction scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Toxicity schema records, opinion extraction, required-param validation, lower-is-better toxic verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Bias schema records, opinion extraction, required-param validation, lower-is-better biased verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Summarization schema records plus required-param validation, alignment/coverage score breakdown, strict mode, prompts, and model-backed prompt/parse execution path
- Task completion schema records plus fallback task/outcome extraction, verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Argument correctness schema records plus required-param validation, empty-tool handling, verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Prompt alignment schema records plus instruction validation, required-param validation, verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Non-advice schema records plus advice-type validation, required-param validation, advice extraction, verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- Role violation schema records plus role validation, required-param validation, violation extraction, binary verdict scoring, prompts, and model-backed prompt/parse execution path
- Misuse schema records plus domain validation, required-param validation, misuse extraction, lower-is-better verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- PII leakage schema records plus required-param validation, PII extraction, privacy-preservation verdict scoring, strict mode, prompts, and model-backed prompt/parse execution path
- MCP use schema records plus required-param validation, map-based primitive formatting, primitive/argument min-score calculation, strict mode, prompts, and model-backed prompt/parse execution path
- Multi-turn MCP use conversational metric with MCP-server validation, unit-interaction task extraction, primitive/argument average scoring, strict mode, final reason generation, and model-backed prompt/parse execution path
- MCP task completion conversational metric shell with MCP-server validation, unit-interaction task extraction, task-score averaging, strict mode, reason joining, and model-backed prompt/parse execution path
- GEval schema records plus required-param/config validation, evaluation-step generation, score normalization, strict mode, rubric-aware prompts, and model-backed prompt/parse execution path
- Step efficiency trace field, schema records, trace JSON prompts, strict mode, and model-backed prompt/parse execution path
- Plan quality schema records, trace plan extraction, empty-plan handling, strict mode, and model-backed prompt/parse execution path
- Plan adherence schema record, trace plan extraction, empty-plan handling, strict mode, and model-backed prompt/parse execution path
- Local tracing API with nested `Tracer.observe(...)` spans, `@Observe` annotation metadata, error capture, and DeepEval-style trace maps attachable to `LlmTestCase.trace`
- Role adherence conversational metric with verdict/reason schemas, assistant-turn scoring, strict mode, chatbot-role validation, and model-backed prompt/parse execution path
- Conversation completeness conversational metric with intention/verdict/reason schemas, valid-verdict scoring, strict mode, and model-backed prompt/parse execution path
- Topic adherence conversational metric with QA-pair/relevancy/reason schemas, unit-interaction QA extraction, truth-table scoring, strict mode, and model-backed prompt/parse execution path
- Knowledge retention conversational metric with knowledge/verdict/reason schemas, user-fact extraction, accumulated-knowledge checks, retained-count scoring, strict mode, and model-backed prompt/parse execution path
- Goal accuracy conversational metric with goal/plan score schemas, unit-interaction goal-step formatting, goal and plan average scoring, strict mode, and model-backed prompt/parse execution path
- Tool use conversational metric with user/tool interaction formatting, tool-selection and argument-correctness schemas, min-average scoring, strict mode, and model-backed prompt/parse execution path
- Conversational GEval metric with default turn content/role params, conversation-level field formatting, strict mode, rubric-aware score normalization, and model-backed prompt/parse execution path
- Arena GEval metric with masked contestant formatting, winner schema parsing, real-name winner mapping, rewritten reason schema parsing, and model-backed prompt/parse execution path
- Arena evaluator API with arena metric interface, arena metric results, batch arena evaluation, assertion errors, and Arena GEval evaluator integration
- Turn relevancy conversational metric with unit-interaction sliding windows, verdict/reason schemas, strict mode, no-interaction scoring, and model-backed prompt/parse execution path
- Turn contextual relevancy conversational metric with retrieval-context collection from sliding unit-interaction windows, contextual verdict/reason reuse, strict mode, no-context scoring, and model-backed prompt/parse execution path
- Turn contextual recall conversational metric with expected-outcome validation, retrieval-context collection from sliding unit-interaction windows, recall verdict/reason reuse, strict mode, no-context scoring, and model-backed prompt/parse execution path
- Turn contextual precision conversational metric with expected-outcome validation, retrieval-context collection from sliding unit-interaction windows, ranked average-precision scoring, strict mode, no-context scoring, and model-backed prompt/parse execution path
- Turn faithfulness conversational metric with retrieval-context collection from sliding unit-interaction windows, truth/claim/verdict/reason reuse, empty-claim local scoring, ambiguous-claim penalty, strict mode, no-context scoring, and model-backed prompt/parse execution path
- DAG node and graph value surface with verdict/task/binary/non-binary validation, parent/indegree wiring including nested verdict child indegree, and root validation
- Conversational DAG node value surface with verdict/task/binary/non-binary validation, turn windows, parent/indegree wiring, and nested verdict child indegree
- Conversational DAG graph wrapper with multiturn root storage and judgement-root validation
- Conversational DAG utility validation from roots, required multi-turn evaluation-param extraction, and identity-preserving graph copy
- DAG graph structure serialization to DeepEval-style node maps/JSON and single-turn task/binary/non-binary/verdict round-trip deserialization, including node/child type enums, judgement labels, and shared node references
- Conversational DAG graph structure serialization to DeepEval-style node maps/JSON with multi-turn evaluation params, turn windows, labels, and shared node references
- DAG utility validation from roots, required single-turn evaluation-param extraction, and identity-preserving graph copy
- Conversational DAG turn-window validation against conversation length before metric execution
- DAG schema records/parsers for metric score reason, task node output, binary judgement verdict, and non-binary judgement verdict
- DAG metric constructor shell with graph validation/copying, required-param extraction, strict-mode threshold, DeepEval-style name suffix, pre-execution required-param validation, task source validation, deterministic score-only verdict-root execution, multi-root last-score execution, shared-child indegree execution, and verdict DAG-node/metric child delegation
- DAG metric deterministic verdict reason generation through `MetricScoreReason` parsing when `includeReason` is enabled
- DAG metric model-backed TaskNode to BinaryJudgementNode execution path with task-output prompt context, binary verdict parsing, and deterministic verdict scoring
- DAG metric model-backed TaskNode to NonBinaryJudgementNode execution path with non-binary verdict parsing and deterministic verdict scoring
- Conversational DAG metric constructor shell with graph validation/copying, required multi-turn param extraction, strict-mode threshold, DeepEval-style name suffix, pre-execution required-param validation, task source validation, deterministic score-only verdict-root execution, multi-root last-score execution, shared-child indegree execution, and verdict DAG-node/metric child delegation
- Conversational DAG metric deterministic verdict reason generation through `MetricScoreReason` parsing when `includeReason` is enabled
- Conversational DAG metric model-backed ConversationalTaskNode to ConversationalBinaryJudgementNode execution path with turn-param prompt context, binary verdict parsing, and deterministic verdict scoring
- Conversational DAG metric model-backed ConversationalTaskNode to ConversationalNonBinaryJudgementNode execution path with non-binary verdict parsing and deterministic verdict scoring
