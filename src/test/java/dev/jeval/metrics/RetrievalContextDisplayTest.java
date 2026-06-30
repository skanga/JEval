package dev.jeval.metrics;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.jeval.MllmImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalContextDisplayTest {

    @Test
    void idRetrievalContextPrefixesTextAndImageNodesLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");

        var annotated = RetrievalContextDisplay.idRetrievalContext(List.of("first context", image, "second context"));

        assertAll(
                () -> assertEquals("Node 1: first context", annotated.get(0)),
                () -> assertEquals("Node 2:", annotated.get(1)),
                () -> assertSame(image, annotated.get(2)),
                () -> assertEquals("Node 3: second context", annotated.get(3)));
    }

    @Test
    void idRetrievalContextExpandsMultimodalPlaceholdersBeforeNumberingLikeDeepEval() {
        var image = new MllmImage("YWJj", "image/png");

        var annotated = RetrievalContextDisplay.idRetrievalContext(List.of("before " + image + " after"));

        assertAll(
                () -> assertEquals("Node 1: before ", annotated.get(0)),
                () -> assertEquals("Node 2:", annotated.get(1)),
                () -> assertSame(image, annotated.get(2)),
                () -> assertEquals("Node 3:  after", annotated.get(3)));
    }
}
