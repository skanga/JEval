package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class BoolQ {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            """
            Q: do iran and afghanistan speak the same language?
            P: Persian is one of the Western Iranian languages and is primarily spoken in Iran, Afghanistan, and Tajikistan.
            A: Yes
            """,
            """
            Q: is elder scrolls online the same as skyrim?
            P: The Elder Scrolls Online occurs before Skyrim and has a broadly similar structure, but it is a separate game.
            A: No
            """,
            """
            Q: do good samaritan laws protect those who help at an accident?
            P: Good Samaritan laws offer legal protection to people who give reasonable assistance to those in peril.
            A: Yes
            """,
            """
            Q: is windows movie maker part of windows essentials?
            P: Windows Movie Maker is a discontinued video editing software and part of Windows Essentials.
            A: Yes
            """,
            """
            Q: can you use oyster card at epsom station?
            P: Epsom railway station is not in the London Oyster card zone.
            A: No
            """);
    private static final String CONFINEMENT_INSTRUCTIONS = "Make sure to output only 'Yes' or 'No'.";
    private final List<Golden> goldens;
    private final int nProblems;
    private final int nShots;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public BoolQ(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size());
    }

    public BoolQ(List<Golden> goldens, int nProblems) {
        this(goldens, nProblems, 5);
    }

    public BoolQ(List<Golden> goldens, int nProblems, int nShots) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("BoolQ only supports nShots between 0 and 5");
        }
        this.goldens = List.copyOf(goldens);
        this.nProblems = nProblems;
        this.nShots = nShots;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        var rows = new ArrayList<BenchmarkPrediction>();
        var correct = 0;
        for (var golden : goldens.subList(0, nProblems)) {
            var prediction = model.generate(prompt(golden.input()));
            var score = Scorer.exactMatchScore(golden.expectedOutput(), prediction);
            correct += score;
            rows.add(new BenchmarkPrediction(golden.input(), prediction, golden.expectedOutput(), score));
        }
        overallScore = (double) correct / nProblems;
        predictions = List.copyOf(rows);
        return new BenchmarkResult(overallScore);
    }

    public List<BenchmarkPrediction> predictions() {
        return predictions == null ? null : List.copyOf(predictions);
    }

    public Double overallScore() {
        return overallScore;
    }

    private String prompt(String input) {
        var prompt = new StringBuilder();
        for (var i = 0; i < nShots; i++) {
            prompt.append(N_SHOT_EXAMPLES.get(i)).append('\n');
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
