package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class ARC {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            "Which factor will most likely cause a person to develop a fever?\n"
                    + "A. a leg muscle relaxing after exercise\n"
                    + "B. a bacterial population in the bloodstream\n"
                    + "C. several viral particles on the skin\n"
                    + "D. carbohydrates being digested in the stomach\n"
                    + "Answer:  B\n\n",
            "Lichens are symbiotic organisms made of green algae and fungi. What do the green algae supply to the fungi in this symbiotic relationship?\n"
                    + "A. carbon dioxide\nB. food\nC. protection\nD. water\nAnswer:  B\n\n",
            "When a switch is used in an electrical circuit, the switch can\n"
                    + "A. cause the charge to build.\n"
                    + "B. increase and decrease the voltage.\n"
                    + "C. cause the current to change direction.\n"
                    + "D. stop and start the flow of current.\n"
                    + "Answer:  D\n\n",
            "Which of the following is an example of an assistive device?\n"
                    + "A. contact lens\nB. motorcycle\nC. raincoat\nD. coffee pot\nAnswer:  A\n\n",
            "Rocks are classified as igneous, metamorphic, or sedimentary according to\n"
                    + "1. their color\n2. their shape\n3. how they formed\n4. the minerals they contain\nAnswer:  3\n\n");
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output 'A', 'B', 'C', or 'D'. Full answer not needed.";
    private final List<Golden> goldens;
    private final int nProblems;
    private final ARCMode mode;
    private final int nShots;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public ARC(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size(), ARCMode.EASY);
    }

    public ARC(List<Golden> goldens, int nProblems, ARCMode mode) {
        this(goldens, nProblems, mode, 5);
    }

    public ARC(List<Golden> goldens, int nProblems, ARCMode mode, int nShots) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("ARC only supports nShots between 0 and 5");
        }
        this.goldens = List.copyOf(goldens);
        this.nProblems = nProblems;
        this.mode = mode == null ? ARCMode.EASY : mode;
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

    public ARCMode mode() {
        return mode;
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
