package dev.jeval.benchmarks;

import dev.jeval.EvaluationModel;
import dev.jeval.Golden;
import dev.jeval.scorer.Scorer;
import java.util.ArrayList;
import java.util.List;

public final class LAMBADA {
    private static final List<String> N_SHOT_EXAMPLES = List.of(
            "Context: her pay for the evening was almost double that of the wait staff and although that might not seem like a lot to some people , it was a small fortune to claire . after loading her final tray for a server , claire went to the restroom to freshen up and begin preparations for being loaded into the cake . pam had a couple of young men from college who assisted her into the cake .\nTarget Sentence: brian and max were a lot of fun and always made her laugh as they hoisted her up to the top of the ____ \nTarget Word: cake\n\n",
            "Context: `` nineteen , '' she said , and he loosed a breath that could have been sadness or relief or maybe both , and told her that made her magic even more impressive . she debated saying that he would be less impressed once he learned of her nickname for him , but winked at him instead .\nTarget Sentence: rowan was frowning when she caught up to him , but said nothing . as they walked away , gavriel murmured , `` good luck , ____ \nTarget Word: rowan\n\n",
            "Context: my assessment of being dead before lunch was n't too far off base . irritably , ezra shook his head , stalking toward `` our '' mat .\nTarget Sentence: `` what kind of training have you had ? '' i gulped , and hurried to catch up . `` uh , none . '' `` perfect , '' he muttered , facing me on the ____ \nTarget Word: mat\n\n",
            "Context: ` just in case there 's trouble , ' he grunted to sparhawk before the party left the chapterhouse . the day was cold and raw the sky was leaden , and a chill wind whistled through the streets of cimmura as vanion led them towards the palace . there were few people abroad in the streets .\nTarget Sentence: sparhawk could not be sure if the citizens were staying inside because of the weather or because some rumours had leaked out about the possibility of ____ \nTarget Word: trouble\n\n",
            "Context: they are racially mixed and all have their mbas , but some of them have other traits i appreciate , as well . but enough of that . where did you get the name arrow ? '' arrow had recovered her poise .\nTarget Sentence: she said , `` my mother was an olympic archer . i guess she hoped she would hit a bull 's - eye with me , just as she does with her other ____ \nTarget Word: arrows\n\n");
    private static final String CONFINEMENT_INSTRUCTIONS =
            "Output the target word! Do not include punctuations.";
    private final List<Golden> goldens;
    private final int nProblems;
    private final int nShots;
    private List<BenchmarkPrediction> predictions;
    private Double overallScore;

    public LAMBADA(List<Golden> goldens) {
        this(goldens, goldens == null ? 0 : goldens.size());
    }

    public LAMBADA(List<Golden> goldens, int nProblems) {
        this(goldens, nProblems, 5);
    }

    public LAMBADA(List<Golden> goldens, int nProblems, int nShots) {
        if (goldens == null || goldens.isEmpty()) {
            throw new IllegalArgumentException("'goldens' must not be empty");
        }
        if (nProblems < 1 || nProblems > goldens.size()) {
            throw new IllegalArgumentException("'nProblems' must be between 1 and the number of goldens");
        }
        if (nShots < 0 || nShots > 5) {
            throw new IllegalArgumentException("LAMBADA only supports nShots between 0 and 5");
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
            var prediction = model.generate(prompt(golden.input(), nShots));
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

    private static String prompt(String input, int nShots) {
        var prompt = new StringBuilder();
        for (var i = 0; i < nShots; i++) {
            prompt.append(N_SHOT_EXAMPLES.get(i));
        }
        return prompt.append(input).append("\n\n").append(CONFINEMENT_INSTRUCTIONS).toString();
    }
}
