package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class Winogrande {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            "Sentence: Ian volunteered to eat Dennis's menudo after already having a bowl because _ despised eating intestine.\n"
                    + "A. Ian\nB. Dennis\nAnswer:B\n\n",
            "Sentence: He never comes to my home, but I always go to his house because the _ is smaller.\n"
                    + "A. home\nB. house\nAnswer:A\n\n",
            "Sentence: Kyle doesn't wear leg warmers to bed, while Logan almost always does. _ is more likely to live in a warmer climate.\n"
                    + "A. Kyle\nB. Logan\nAnswer:A\n\n",
            "Sentence: The treasury workers took the gold bars off of the trolley and stacked them in the safe until the _ was empty.\n"
                    + "A. safe\nB. trolley\nAnswer:B\n\n",
            "Sentence: Emily looked up and saw Patricia racing by overhead, as _ was on the ramp .\n"
                    + "A. Emily\nB. Patricia\nAnswer:B\n\n");
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output 'A', 'B', 'C', or 'D'. Full answer not needed.";
    private final List<Golden> goldens;
    private final int nProblems;
    private final int nShots;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public Winogrande(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size());
    }

    public Winogrande(List<Golden> goldens, int nProblems) {
        this(goldens, nProblems, 5);
    }

    public Winogrande(List<Golden> goldens, int nProblems, int nShots) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("Winogrande only supports nShots between 0 and 5");
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
            prompt.append(N_SHOT_EXAMPLES.get(i));
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
