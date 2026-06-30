package dev.jeval.simulator;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class SimulationGraphTemplate {
    private SimulationGraphTemplate() {
    }

    public static String classifyEdge(String assistantReply, List<String> choices) {
        var values = choices == null ? List.<String>of() : choices;
        var numberedChoices = IntStream.range(0, values.size())
                .mapToObj(index -> "  " + (index + 1) + ") " + values.get(index))
                .collect(Collectors.joining("\n"));
        var noneIndex = values.size() + 1;
        return """
                You are routing a simulated conversation. The assistant just said:
                <<<
                %s
                >>>

                Which of the following best describes the assistant's reply?
                %s
                  %d) None of the above

                Pick exactly one option. Respond with the option's number.

                IMPORTANT: The output must be formatted as a JSON object with two keys:
                - `index`: the 1-based number of the option you chose, OR `null` if you chose "None of the above" (option %d).
                - `reason`: a short rationale (one sentence).

                Example JSON Output for a match:
                {
                    "index": 1,
                    "reason": "The reply explicitly approved the refund."
                }

                Example JSON Output for no match:
                {
                    "index": null,
                    "reason": "The reply asked a clarifying question that does not fit any option."
                }

                JSON Output:
                """.formatted(assistantReply, numberedChoices, noneIndex, noneIndex);
    }
}
