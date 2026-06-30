package dev.jeval.metrics;

import dev.jeval.MllmImage;
import java.util.ArrayList;
import java.util.List;

public final class RetrievalContextDisplay {
    private RetrievalContextDisplay() {
    }

    public static List<Object> idRetrievalContext(List<?> retrievalContext) {
        var annotated = new ArrayList<>();
        var sequence = toMultimodalArray(retrievalContext);
        for (var i = 0; i < sequence.size(); i++) {
            var context = sequence.get(i);
            if (context instanceof String text) {
                annotated.add("Node " + (i + 1) + ": " + text);
            } else if (context instanceof MllmImage image) {
                annotated.add("Node " + (i + 1) + ":");
                annotated.add(image);
            }
        }
        return List.copyOf(annotated);
    }

    private static List<Object> toMultimodalArray(List<?> retrievalContext) {
        var parts = new ArrayList<>();
        for (var context : retrievalContext) {
            if (context instanceof String text) {
                parts.addAll(MllmImage.parseMultimodalString(text));
            } else if (context instanceof MllmImage) {
                parts.add(context);
            }
        }
        return parts;
    }
}
