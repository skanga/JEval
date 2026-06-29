package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class Winogrande {
    private final List<Golden> goldens;
    private final int nProblems;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public Winogrande(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size());
    }

    public Winogrande(List<Golden> goldens, int nProblems) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        this.goldens = List.copyOf(goldens);
        this.nProblems = nProblems;
    }

    public BenchmarkResult evaluate(EvaluationModel model) {
        if (model == null) {
            throw new IllegalArgumentException("'model' must not be null");
        }
        var rows = new ArrayList<BenchmarkPrediction>();
        var correct = 0;
        for (var golden : goldens.subList(0, nProblems)) {
            var prediction = model.generate(golden.input());
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
}
