package dev.jeval.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

final class StepEfficiencyPrompts {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality when analyzing the trace.
            """;

    private StepEfficiencyPrompts() {
    }

    static String extractTaskFromTrace(Map<String, Object> trace) {
        return """
                You are a **trace analyst** tasked with extracting the user's original goal or task from a complete nested execution trace of an AI agent.

                YOUR OBJECTIVE:
                Identify exactly what the user asked the agent to do, based only on the user's explicit input and unambiguous contextual details in the trace.
                Produce a concise, fact-based statement of the intended user task, not the agent's plan, actions, reasoning, or assumptions.

                STRICT EXTRACTION RULES:
                1. Primary Source: Root-Level User Input. Derive the task directly and primarily from the root agent's "input" field.
                2. Secondary Context: use child spans only to clarify or disambiguate what the user explicitly asked for.
                3. No Hallucination: do not invent goals, assumptions, or implied needs beyond what is explicitly or clearly inferable.
                4. Agent-Agnostic Rule: ignore tools, methods, reasoning, and internal operations.
                5. Perspective: express the task from the user's perspective.
                6. Fallback: if only raw user input is available, return that input verbatim.

                OUTPUT FORMAT:

                Return **only** a JSON object of this form:

                {
                  "task": "<a single clear sentence summarizing the user's explicit goal>"
                }

                - The "task" value should be a single, coherent natural language sentence or two at most.
                - Do not include commentary, metadata, or any additional fields.

                EXAMPLES:

                Example Trace: {
                  "name": "trip_planner",
                  "type": "agent",
                  "input": {
                    "input": "Help me plan a business trip to Chicago next week."
                  }
                }

                Expected JSON:
                {
                  "task": "Plan a business trip to Chicago next week."
                }

                When uncertain, extract **less rather than more** and prefer minimal, factual phrasing over speculative completion.
                %s

                TRACE:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, toJson(trace));
    }

    static String getExecutionEfficiency(String task, Map<String, Object> trace) {
        return """
                You are an **efficiency auditor** evaluating how economically an AI agent executed a task.

                OBJECTIVE:
                Determine how **efficiently** the agent executed the given task based on its full execution trace.
                Efficiency means achieving the user's goal using the **fewest, simplest, and most direct** actions possible.
                You must assign a score from **0.0 to 1.0** that reflects how close the execution came to the minimal necessary sequence of actions.
                You are not evaluating correctness, completeness, creativity, or helpfulness, only the efficiency of the execution.

                STRICT EVALUATION RULES:
                1. Zero-Tolerance for Unnecessary Actions: every step, tool call, LLM query, or retrieval must be strictly required.
                If a single tool, retrieval, or reasoning step is superfluous, speculative, repetitive, or stylistic, the score must be as low as possible regardless of outcome quality.
                Adding helpful or contextual actions that were not explicitly necessary is an inefficiency.
                2. Minimal Action Principle: the ideal execution performs the exact minimum number of steps needed to complete the task.
                Each step must directly contribute to completing the task, not to exploration, confirmation, or elaboration.
                3. No Speculation or Enrichment: expansion, beautification, or extra context lowers the score sharply.
                Efficiency is about restraint: doing exactly what's required, nothing more.
                4. Directness and Focus: repetition, re-querying, nested reasoning loops, or unnecessary tool reuse indicate inefficiency.
                5. Resource Economy: multiple LLM calls, retrievers, or tools when one would suffice must be penalized.
                6. When in Doubt: assume unclear actions were unnecessary and lower the score.
                %s

                SCORING SCALE (STRICT):
                - 1.0: Perfectly efficient.
                - 0.75: Strong efficiency.
                - 0.5: Moderate efficiency.
                - 0.25: Low efficiency.
                - 0.0: Highly inefficient.

                When uncertain, always assign the lower score.

                OUTPUT FORMAT:

                Return a single JSON object in this exact format:

                {
                  "score": 0.0,
                  "reason": "1-3 concise factual sentences describing where inefficiencies occurred."
                }

                The reason must identify specific inefficient actions and avoid subjective phrasing.

                EXAMPLES

                Example 1:
                Task: "Summarize the given text."
                Trace: Agent calls an LLM twice, then performs an extra web search.

                Output:
                {
                  "score": 0.25,
                  "reason": "The agent used redundant LLM calls and performed an unnecessary web search. Only one LLM call was required for the summary."
                }

                Example 2:
                Task: "Convert a date to ISO format."
                Trace: Agent performs one computation directly.

                Output:
                {
                  "score": 1.0,
                  "reason": "The agent completed the task with one minimal action and no unnecessary steps."
                }

                FINAL REMINDERS:
                - Efficiency = minimality. Any extra work, enrichment, or indirect approach must lower the score.
                - Do not consider correctness, helpfulness, or reasoning quality.
                - A "good answer" can still score **0.0** if it was achieved inefficiently.
                - This metric is adversarial: assign the lowest score possible unless execution was provably minimal.

                TASK:
                %s

                TRACE:
                %s

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, task, toJson(trace));
    }

    static String toJson(Map<String, Object> trace) {
        try {
            return MAPPER.writeValueAsString(trace);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Trace must be JSON serializable", e);
        }
    }
}
