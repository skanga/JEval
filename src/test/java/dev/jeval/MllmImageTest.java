package dev.jeval;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MllmImageTest {

    @TempDir
    Path tempDir;

    @Test
    void localImageLoadsBase64AndMimeType() throws IOException {
        var imagePath = tempDir.resolve("car.png");
        var bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        Files.write(imagePath, bytes);

        var image = new MllmImage(imagePath.toString());

        assertAll(
                () -> assertEquals("car.png", image.filename()),
                () -> assertEquals("image/png", image.mimeType()),
                () -> assertEquals(Base64.getEncoder().encodeToString(bytes), image.dataBase64()),
                () -> assertTrue(image.local()));
    }

    @Test
    void remoteImageUrlKeepsUrlWithoutLoadingBase64() {
        var image = new MllmImage("https://example.com/assets/car.png");

        assertAll(
                () -> assertEquals("car.png", image.filename()),
                () -> assertEquals("image/png", image.mimeType()),
                () -> assertEquals(null, image.dataBase64()),
                () -> assertEquals(false, image.local()));
    }

    @Test
    void remoteImageWithUnknownExtensionHasNoMimeType() {
        var image = new MllmImage("https://example.com/assets/car");

        assertEquals(null, image.mimeType());
    }

    @Test
    void remoteImageUsesMimetypesGuessLikeDeepEval() {
        var image = new MllmImage("https://example.com/assets/icon.svg");

        assertEquals("image/svg+xml", image.mimeType());
    }

    @Test
    void remoteImageKeepsEncodedFilenameLikeDeepEval() {
        var image = new MllmImage("https://example.com/assets/my%20icon.svg");

        assertEquals("my%20icon.svg", image.filename());
    }

    @Test
    void base64ImageKeepsProvidedDataAndMimeType() {
        var image = new MllmImage("YWJj", "image/png");

        assertAll(
                () -> assertEquals("YWJj", image.dataBase64()),
                () -> assertEquals("image/png", image.mimeType()),
                () -> assertEquals(null, image.url()),
                () -> assertEquals(false, image.local()),
                () -> assertTrue(image.toString().startsWith("[DEEPEVAL:IMAGE:")));
    }

    @Test
    void base64ImageReturnsDataUri() {
        var image = new MllmImage("YWJj", "image/png");

        assertEquals("data:image/png;base64,YWJj", image.asDataUri());
    }

    @Test
    void remoteImageHasNoDataUri() {
        var image = new MllmImage("https://example.com/assets/car.png");

        assertEquals(null, image.asDataUri());
    }

    @Test
    void rejectsMissingUrlLikeDeepEval() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new MllmImage((String) null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new MllmImage("")));
    }

    @Test
    void rejectsBase64ImageWithoutMimeType() {
        assertThrows(IllegalArgumentException.class, () -> new MllmImage("YWJj", null));
    }

    @Test
    void placeholderMarksLlmTestCaseAsMultimodal() throws IOException {
        var imagePath = tempDir.resolve("car.png");
        Files.write(imagePath, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        var image = new MllmImage(imagePath.toString());

        var testCase = LlmTestCase.builder("What is shown? " + image)
                .actualOutput("A car")
                .build();

        assertTrue(testCase.multimodal());
    }

    @Test
    void parsesPlaceholderBackToRegisteredImage() throws IOException {
        var imagePath = tempDir.resolve("car.png");
        Files.write(imagePath, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        var image = new MllmImage(imagePath.toString());

        var parts = MllmImage.parseMultimodalString("before " + image + " after");

        assertAll(
                () -> assertEquals("before ", parts.get(0)),
                () -> assertSame(image, parts.get(1)),
                () -> assertEquals(" after", parts.get(2)));
    }

    @Test
    void parsesUnknownUrlPlaceholderAsRemoteImage() {
        var parts = MllmImage.parseMultimodalString(
                "before [DEEPEVAL:IMAGE:https://example.com/assets/orphan.png] after");

        var image = (MllmImage) parts.get(1);

        assertAll(
                () -> assertEquals("before ", parts.get(0)),
                () -> assertEquals("https://example.com/assets/orphan.png", image.url()),
                () -> assertEquals("orphan.png", image.filename()),
                () -> assertEquals("[DEEPEVAL:IMAGE:https://example.com/assets/orphan.png]", image.toString()),
                () -> assertEquals(" after", parts.get(2)));
    }

    @Test
    void rejectsInvalidRemoteUrl() {
        assertThrows(IllegalArgumentException.class, () -> new MllmImage("ftp://example.com/image.png"));
    }
}
