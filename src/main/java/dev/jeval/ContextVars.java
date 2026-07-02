package dev.jeval;

public final class ContextVars {
    private static final ThreadLocal<Golden> CURRENT_GOLDEN = new ThreadLocal<>();

    private ContextVars() {
    }

    public static Token setCurrentGolden(Golden golden) {
        var token = new Token(CURRENT_GOLDEN.get());
        if (golden == null) {
            CURRENT_GOLDEN.remove();
        } else {
            CURRENT_GOLDEN.set(golden);
        }
        return token;
    }

    public static Golden getCurrentGolden() {
        return CURRENT_GOLDEN.get();
    }

    public static void resetCurrentGolden(Token token) {
        if (token.previous == null) {
            CURRENT_GOLDEN.remove();
        } else {
            CURRENT_GOLDEN.set(token.previous);
        }
    }

    public record Token(Golden previous) {
    }
}
