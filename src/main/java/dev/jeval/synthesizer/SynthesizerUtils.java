package dev.jeval.synthesizer;

public final class SynthesizerUtils {
    private static final String RESET = "\033[0m";
    private static final String DIM = "\033[2m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";

    private SynthesizerUtils() {
    }

    public static void printSynthesizerStatus(SynthesizerStatus status, String message) {
        printSynthesizerStatus(status, message, null);
    }

    public static void printSynthesizerStatus(SynthesizerStatus status, String message, String description) {
        var prefix = DIM + "[Confident AI Synthesizer Log]" + RESET;
        var label = switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILURE -> "FAILURE";
            case WARNING -> "WARNING";
        };
        var color = switch (status) {
            case SUCCESS -> GREEN;
            case FAILURE -> RED;
            case WARNING -> YELLOW;
        };
        var text = prefix + " " + label + ": " + color + message + RESET;
        System.out.println(description == null || description.isBlank() ? text : text + ": " + description);
    }
}
