package dev.jeval.scorer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScorerTest {

    @Test
    void exactMatchScoreTrimsOnlyAndRejectsMissingPrediction() {
        assertEquals(1, Scorer.exactMatchScore(" answer ", "answer"));
        assertEquals(0, Scorer.exactMatchScore("Answer", "answer"));
        assertEquals(0, Scorer.exactMatchScore("answer", ""));
        assertEquals(0, Scorer.exactMatchScore("answer", null));
    }

    @Test
    void whitespacePredictionIsNotMissingLikeDeepEvalScorer() {
        assertEquals(1, Scorer.exactMatchScore("", " "));
        assertEquals(1, Scorer.quasiExactMatchScore("", " "));
        assertEquals(1, Scorer.quasiContainsScore(List.of(""), " "));
    }

    @Test
    void quasiExactMatchNormalizesArticlesPunctuationCaseAndWhitespace() {
        assertEquals(1, Scorer.quasiExactMatchScore("The quick, brown fox!", "quick brown fox"));
        assertEquals(0, Scorer.quasiExactMatchScore("quick brown fox", "slow brown fox"));
        assertEquals(0, Scorer.quasiExactMatchScore("quick brown fox", ""));
    }

    @Test
    void quasiContainsChecksNormalizedPredictionAgainstTargets() {
        assertEquals(1, Scorer.quasiContainsScore(List.of("The answer.", "Backup"), "answer"));
        assertEquals(0, Scorer.quasiContainsScore(List.of("The answer."), "other"));
        assertEquals(0, Scorer.quasiContainsScore(List.of("The answer."), null));
    }

    @Test
    void truthIdentificationScoreReturnsRoundedPercentOfTargetMatches() {
        assertEquals(67, Scorer.truthIdentificationScore("[1,2,3]", "[1,3]"));
        assertEquals(100, Scorer.truthIdentificationScore("1,2", "2,1"));
        assertEquals(0, Scorer.truthIdentificationScore("[1,2]", ""));
        assertEquals(0, Scorer.truthIdentificationScore("", "[1]"));
        assertEquals(0, Scorer.truthIdentificationScore("[1,2]", "nope"));
    }

    @Test
    void truthIdentificationRequiresDeepEvalBracketFormatting() {
        assertEquals(0, Scorer.truthIdentificationScore(" [1,2] ", "[1]"));
    }

    @Test
    void truthIdentificationStripsRepeatedEdgeBracketsLikeDeepEval() {
        assertEquals(100, Scorer.truthIdentificationScore("[[1]]", "[1]"));
    }

    @Test
    void passAtKMatchesDeepEvalProductFormula() {
        assertEquals(1.0, Scorer.passAtK(3, 2, 2));
        assertEquals(0.5333333333333334, Scorer.passAtK(10, 3, 2), 1e-12);
        assertEquals(0.0, Scorer.passAtK(5, 0, 1));
    }
}
