package dev.jeval.metrics.dag;

import dev.jeval.Turn;
import java.util.List;

public record TurnWindow(int start, int end) {
    public void validateAgainst(List<Turn> turns) {
        if (start > end
                || start == end
                || end - start >= turns.size()
                || start < 0
                || end < 0
                || end == turns.size()) {
            throw new IllegalArgumentException(
                    "The 'turn_window' passed is invalid. Please recheck your 'turn_window' values.");
        }
    }
}
