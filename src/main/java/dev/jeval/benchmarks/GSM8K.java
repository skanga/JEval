package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class GSM8K {
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Make sure to output only the numerical answer.";
    private final List<Golden> goldens;
    private final int nProblems;
    private final List<Golden> shotGoldens;
    private final int nShots;
    private final boolean enableCot;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public GSM8K(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size());
    }

    public GSM8K(List<Golden> goldens, int nProblems) {
        this(goldens, nProblems, List.of(), 0, true);
    }

    public GSM8K(List<Golden> goldens, int nProblems, List<Golden> shotGoldens, int nShots, boolean enableCot) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        if (shotGoldens == null) {
            throw new IllegalArgumentException("'shotGoldens' must not be null");
        }
        if (nShots < 0 || nShots > 15) {
            throw new IllegalArgumentException("GSM8K only supports nShots between 0 and 15");
        }
        if (nShots > shotGoldens.size()) {
            throw new IllegalArgumentException("'nShots' must not exceed shotGoldens size");
        }
        this.goldens = List.copyOf(goldens);
        this.nProblems = nProblems;
        this.shotGoldens = List.copyOf(shotGoldens);
        this.nShots = nShots;
        this.enableCot = enableCot;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        var rows = new ArrayList<BenchmarkPrediction>();
        var correct = 0;
        for (var golden : goldens.subList(0, nProblems)) {
            var prediction = model.generate(prompt(golden.input(), shotGoldens, nShots, enableCot));
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

    private static String prompt(String input, List<Golden> shotGoldens, int nShots, boolean enableCot) {
        var prompt = new StringBuilder();
        if (nShots > 0) {
            prompt.append("The following are grade school math word problems\n\n");
        }
        for (var i = 0; i < nShots; i++) {
            prompt.append(formatExample(shotGoldens.get(i), enableCot)).append("\n\n");
        }
        prompt.append("**Problem**: ").append(input).append("\n**Answer**: \n\n")
                .append(enableCot ? "Let's think step-by-step." : "No explanation needed.")
                .append("\n\n")
                .append(CONFINEMENT_INSTRUCTIONS);
        return prompt.toString();
    }

    private static String formatExample(Golden golden, boolean enableCot) {
        var rawAnswer = golden.actualOutput();
        if (rawAnswer == null) {
            rawAnswer = "#### " + golden.expectedOutput();
        }
        var parts = rawAnswer.strip().split("\\R#### ", 2);
        var answer = parts.length == 2 ? parts[1] : golden.expectedOutput();
        var formatted = new StringBuilder("**Problem**: ").append(golden.input()).append('\n');
        if (enableCot && parts.length == 2) {
            formatted.append("**Solution**: ").append(parts[0]).append('\n');
        }
        return formatted.append("**Answer**: ").append(answer).toString();
    }
}
