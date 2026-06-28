package dev.jeval.metrics;

import dev.jeval.ToolCall;
import java.util.List;

final class TaskCompletionPrompts {
    private TaskCompletionPrompts() {
    }

    static String extractTaskAndOutcome(String input, String actualOutput, List<ToolCall> toolsCalled) {
        return """
                Given an agentic workflow comprised of a human input, AI response, and tools used by the AI, identify the task (or objective the user wants to achieve) and the task_outcome (the final outcome or result of the workflow).
                The task outcome should be solely factual, derived strictly from the workflow (input, response, and tools called), without any reasoning involved.

                Example input: Can you help me plan a trip to New York this weekend, including travel, accommodation, and sightseeing?
                Example tools called:
                [
                  {"name": "flight_search", "output": {"flights": ["Flight A", "Flight B"]}},
                  {"name": "hotel_search", "output": {"hotels": ["Grand NY Hotel", "Empire Suites"]}},
                  {"name": "sightseeing_search", "output": {"sights": ["Central Park", "Statue of Liberty", "Times Square"]}}
                ]
                Example response: Sure! Flights available to New York include Flight A and Flight B. Accommodation options include Grand NY Hotel and Empire Suites. Suggested sightseeing spots in New York are Central Park, Statue of Liberty, and Times Square.
                Example JSON:
                {
                  "task": "Have the system plan a weekend trip to New York, including travel, accommodation, and sightseeing.",
                  "outcome": "The system provided suggested flights departing on Saturday and returning on Sunday, identified hotels with check-in on Saturday and check-out on Sunday, and generated a list of sightseeing destinations in New York City."
                }
                ===== END OF EXAMPLE ======

                IMPORTANT: Please make sure to only return in JSON format with two keys: `task` and `outcome`.

                input: %s
                tools called:
                %s
                response: %s

                JSON:
                """.formatted(input, toolsCalled == null ? List.of() : toolsCalled, actualOutput);
    }

    static String generateVerdict(String task, String actualOutcome) {
        return """
                Given the task (desired outcome) and the actual achieved outcome, compare how well the actual outcome aligns with the desired task.

                Please return a JSON with two keys: `verdict` and `reason`.
                - The `verdict` should be a score from 0 to 1, where 1 indicates the actual outcome perfectly achieves the desired task, and 0 indicates it does not achieve the task at all.
                - The `reason` should explain why the given verdict was assigned.

                IMPORTANT: Please make sure to only return in JSON format, with `verdict` as a float between 0 and 1.
                Example:
                Task: Have the system plan a weekend trip to New York, including travel, accommodation, and sightseeing.
                Actual outcome: The system provided suggested flights departing on Saturday and returning on Sunday, identified hotels with check-in on Saturday and check-out on Sunday, and generated a list of sightseeing destinations in New York City.
                Example JSON:
                {
                  "verdict": 0.85,
                  "reason": "The system suggested flights, accommodation, and sightseeing options but did not fully plan the trip as expected."
                }

                Task:
                %s

                Actual outcome:
                %s

                JSON:
                """.formatted(task, actualOutcome);
    }
}
