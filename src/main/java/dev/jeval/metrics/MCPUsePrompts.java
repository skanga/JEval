package dev.jeval.metrics;

import dev.jeval.LlmTestCase;

final class MCPUsePrompts {
    private static final String MULTIMODAL_INPUT_RULES = """

            --- MULTIMODAL INPUT RULES ---
            Some inputs may include text and media placeholders such as images or PDFs. Evaluate every provided modality.
            """;

    private MCPUsePrompts() {
    }

    static String getPrimitiveCorrectnessPrompt(
            LlmTestCase testCase,
            String availablePrimitives,
            String primitivesUsed) {
        return """
                Evaluate whether the tools (primitives) selected and used by the agent were appropriate and correct for fulfilling the user's request.
                Base your judgment on the user input, the agent's visible output, and the tools that were available to the agent.
                You must return a JSON object with exactly two fields: 'score' and 'reason'.
                %s

                Scoring:
                - 'score' is a float between 0 and 1 inclusive.
                - Use intermediate values (e.g., 0.25, 0.5, 0.75) to reflect cases where the tools used were partially correct, suboptimal, or only somewhat relevant.
                - 'reason' should clearly explain how appropriate and correct the chosen primitives were, considering both the user's request and the output.

                IMPORTANT:
                - Focus only on tool selection and usage, not the quality of the final output.
                - Assume that available_primitives contains the only tools the agent could have used.
                - Consider whether the agent:
                - Chose the correct tool(s) for the task.
                - Avoided unnecessary or incorrect tool calls.
                - Missed a more appropriate tool when one was available.
                - Multiple valid tool combinations may exist; give credit when one reasonable strategy is used effectively.

                CHAIN OF THOUGHT:
                1. Determine what the user was asking for from test_case.input.
                2. Evaluate whether the tools in primitives_used were appropriate for achieving that goal.
                3. Consider available_primitives to judge if better options were missed or poor tools were unnecessarily used.
                4. Ignore whether the tool worked; focus only on whether it was the right tool to use.

                You must return only a valid JSON object. Do not include any explanation or text outside the JSON.

                User Input:
                %s

                Agent Visible Output:
                %s

                Available Tools:
                %s

                Tools Used by Agent:
                %s

                Example Output:
                {
                  "score": 0.75,
                  "reason": "The agent used a relevant tool to address the user's request, but a more specific tool was available and would have been more efficient."
                }

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, testCase.input(), testCase.actualOutput(), availablePrimitives, primitivesUsed);
    }

    static String getArgumentCorrectnessPrompt(
            LlmTestCase testCase,
            String availablePrimitives,
            String primitivesUsed) {
        return """
                Evaluate whether the arguments passed to each tool (primitive) used by the agent were appropriate and correct for the intended purpose.
                Focus on whether the input types, formats, and contents match the expectations of the tools and are suitable given the user's request.

                You must return a JSON object with exactly two fields: 'score' and 'reason'.
                %s

                Scoring:
                - 'score' is a float between 0 and 1 inclusive.
                - Use intermediate values (e.g., 0.25, 0.5, 0.75) to reflect partial correctness, such as when argument types were correct but content was misaligned with intent.
                - 'reason' should clearly explain whether the arguments passed to tools were well-formed, appropriate, and aligned with the tool's expected inputs and the user's request.

                IMPORTANT:
                - Assume the selected tools themselves were appropriate.
                - Focus only on input correctness for the tools used.
                - Focus ONLY on:
                - Whether the correct arguments were passed to each tool (types, structure, semantics).
                - Whether any required arguments were missing or malformed.
                - Whether extraneous, irrelevant, or incorrect values were included.
                - Refer to available_primitives to understand expected argument formats and semantics.

                CHAIN OF THOUGHT:
                1. Understand the user's request from test_case.input.
                2. Review the arguments passed to each tool in primitives_used.
                3. Compare the arguments with what each tool in available_primitives expects.
                4. Determine whether each tool was used with suitable and valid inputs.
                5. Do NOT evaluate tool choice or output quality; only input correctness for the tools used.

                You must return only a valid JSON object. Do not include any explanation or text outside the JSON.

                User Input:
                %s

                Agent Visible Output:
                %s

                Available Primitives (with expected arguments and signatures):
                %s

                Primitives Used by Agent (with arguments passed):
                %s

                Example Output:
                {
                  "score": 0.5,
                  "reason": "The agent passed arguments of the correct type to all tools, but one tool received an input that did not match the user's intent and another had a missing required field."
                }

                JSON:
                """.formatted(MULTIMODAL_INPUT_RULES, testCase.input(), testCase.actualOutput(), availablePrimitives, primitivesUsed);
    }
}
