package dev.jeval.models;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public final class RetryPolicy {
    private static final String SDK_RETRY_PROVIDERS = "DEEPEVAL_SDK_RETRY_PROVIDERS";
    private static final Pattern PROVIDER_SEPARATOR = Pattern.compile("[,;\\s]+");
    private static final Pattern SLUG_SEPARATOR = Pattern.compile("[^a-z0-9]+");

    private RetryPolicy() {
    }

    public static ErrorPolicy errorPolicy(
            Set<Class<? extends Throwable>> authExceptions,
            Set<Class<? extends Throwable>> rateLimitExceptions,
            Set<Class<? extends Throwable>> networkExceptions,
            Set<Class<? extends Throwable>> httpExceptions,
            Set<String> nonRetryableCodes,
            boolean retry5xx,
            Map<String, ? extends Iterable<String>> messageMarkers) {
        return new ErrorPolicy(
                authExceptions,
                rateLimitExceptions,
                networkExceptions,
                httpExceptions,
                nonRetryableCodes,
                retry5xx,
                normalizeMarkers(messageMarkers));
    }

    public static String extractErrorCode(Throwable error) {
        return extractErrorCode(error, Map.of());
    }

    public static String extractErrorCode(Throwable error, Map<String, ? extends Iterable<String>> messageMarkers) {
        var grpcCode = grpcCode(error);
        if (!grpcCode.isEmpty()) {
            return grpcCode;
        }
        var response = attribute(error, "response");
        var responseCode = codeFromResponse(response);
        if (!responseCode.isEmpty()) {
            return responseCode;
        }
        var bodyCode = codeAt(attribute(error, "body"), List.of("error", "code"));
        if (!bodyCode.isEmpty()) {
            return bodyCode;
        }
        return codeFromMarkers(String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT),
                normalizeMarkers(messageMarkers));
    }

    public static boolean isTransient(ErrorPolicy policy, Throwable error) {
        return isTransient(policy, error, Set.of(), null);
    }

    public static boolean isTransient(
            ErrorPolicy policy,
            Throwable error,
            Set<String> extraNonRetryableCodes,
            Map<String, ? extends Iterable<String>> messageMarkers) {
        if (matches(policy.authExceptions(), error)) {
            return false;
        }
        if (matches(policy.networkExceptions(), error)) {
            return true;
        }
        if (matches(policy.rateLimitExceptions(), error)) {
            var code = extractErrorCode(error, messageMarkers == null ? policy.messageMarkers() : messageMarkers);
            return !nonRetryableCodes(policy, extraNonRetryableCodes).contains(code);
        }
        if (matches(policy.httpExceptions(), error)) {
            var status = statusCode(error);
            return status != null && status >= 500 && policy.retry5xx();
        }
        return false;
    }

    public static RetrySettings retrySettings() {
        return retrySettings(System.getenv());
    }

    static RetrySettings retrySettings(Map<String, String> env) {
        return new RetrySettings(
                readEnvInt(env, "DEEPEVAL_RETRY_MAX_ATTEMPTS", 2, 1),
                readEnvFloat(env, "DEEPEVAL_RETRY_INITIAL_SECONDS", 1.0, 0.0),
                readEnvFloat(env, "DEEPEVAL_RETRY_EXP_BASE", 2.0, 1.0),
                readEnvFloat(env, "DEEPEVAL_RETRY_JITTER", 2.0, 0.0),
                readEnvFloat(env, "DEEPEVAL_RETRY_CAP_SECONDS", 5.0, 0.0));
    }

    public static double retryDelaySeconds(RetrySettings settings, int attemptNumber) {
        var jitter = settings.jitterSeconds() == 0.0
                ? 0.0
                : ThreadLocalRandom.current().nextDouble(settings.jitterSeconds());
        return retryDelaySeconds(settings, attemptNumber, jitter);
    }

    static double retryDelaySeconds(RetrySettings settings, int attemptNumber, double jitterSeconds) {
        if (settings.capSeconds() == 0.0) {
            return 0.0;
        }
        var exponent = Math.max(0, attemptNumber - 1);
        var baseDelay = settings.initialSeconds() * Math.pow(settings.expBase(), exponent);
        return Math.min(settings.capSeconds(), baseDelay + Math.max(0.0, jitterSeconds));
    }

    public static boolean shouldStopAfterAttempt(RetrySettings settings, int attemptNumber) {
        return attemptNumber >= settings.maxAttempts();
    }

    public static Set<String> sdkRetryProviders() {
        return sdkRetryProviders(System.getenv());
    }

    static Set<String> sdkRetryProviders(Map<String, String> env) {
        var raw = env.get(SDK_RETRY_PROVIDERS);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        var providers = new LinkedHashSet<String>();
        for (var part : PROVIDER_SEPARATOR.split(raw.strip())) {
            var slug = slugify(part);
            if (slug.isEmpty()) {
                continue;
            }
            if ("*".equals(slug)) {
                return Set.of("*");
            }
            providers.add(slug);
        }
        return Set.copyOf(providers);
    }

    public static boolean sdkRetriesFor(String provider, Iterable<?> sdkRetryProviders) {
        var slug = slugify(provider);
        for (var chosen : sdkRetryProviders) {
            var chosenSlug = slugify(String.valueOf(chosen));
            if ("*".equals(chosenSlug) || slug.equals(chosenSlug)) {
                return true;
            }
        }
        return false;
    }

    public static ErrorPolicy getRetryPolicyFor(
            String provider,
            Map<String, ErrorPolicy> policies,
            Iterable<?> sdkRetryProviders) {
        if (sdkRetriesFor(provider, sdkRetryProviders)) {
            return null;
        }
        return policies.get(slugify(provider));
    }

    public static <T> T executeWithRetry(
            String provider,
            Callable<T> operation,
            Map<String, ErrorPolicy> policies) throws Exception {
        return executeWithRetry(provider, operation, policies, sdkRetryProviders(), retrySettings());
    }

    public static <T> T executeWithRetry(
            String provider,
            Callable<T> operation,
            Map<String, ErrorPolicy> policies,
            Iterable<?> sdkRetryProviders,
            RetrySettings settings) throws Exception {
        var policy = getRetryPolicyFor(provider, policies, sdkRetryProviders);
        if (policy == null) {
            return operation.call();
        }

        var attempt = 1;
        while (true) {
            try {
                return operation.call();
            } catch (Exception error) {
                if (!isTransient(policy, error)) {
                    throw error;
                }
                if (shouldStopAfterAttempt(settings, attempt)) {
                    throw new RetryExhaustedException(provider, attempt, error);
                }
                sleepBeforeRetry(retryDelaySeconds(settings, attempt));
                attempt++;
            }
        }
    }

    private static boolean matches(Set<Class<? extends Throwable>> types, Throwable error) {
        for (var type : types) {
            if (type.isInstance(error)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> nonRetryableCodes(ErrorPolicy policy, Set<String> extraCodes) {
        var codes = new LinkedHashSet<String>();
        codes.addAll(policy.nonRetryableCodes());
        if (extraCodes != null) {
            codes.addAll(extraCodes);
        }
        return Set.copyOf(codes);
    }

    private static String codeFromResponse(Object response) {
        if (response == null) {
            return "";
        }
        var awsCode = codeAt(response, List.of("Error", "Code"));
        if (!awsCode.isEmpty()) {
            return awsCode;
        }
        try {
            var json = response.getClass().getDeclaredMethod("json");
            json.setAccessible(true);
            return codeAt(json.invoke(response), List.of("error", "code"));
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException error) {
            return "";
        }
    }

    private static String codeAt(Object value, List<String> path) {
        Object current = value;
        for (var key : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return "";
            }
            current = map.get(key);
        }
        return current instanceof String || current instanceof Number ? String.valueOf(current) : "";
    }

    private static String grpcCode(Throwable error) {
        try {
            var code = error.getClass().getDeclaredMethod("code");
            code.setAccessible(true);
            var status = code.invoke(error);
            var name = attribute(status, "name");
            return name == null ? String.valueOf(status).toLowerCase(Locale.ROOT)
                    : String.valueOf(name).toLowerCase(Locale.ROOT);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return "";
        }
    }

    private static Integer statusCode(Throwable error) {
        var status = attribute(error, "statusCode");
        if (status == null) {
            status = attribute(error, "status_code");
        }
        if (status instanceof Number number) {
            return number.intValue();
        }
        if (status instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void sleepBeforeRetry(double seconds) throws InterruptedException {
        if (seconds <= 0.0) {
            return;
        }
        var totalNanos = Math.round(seconds * 1_000_000_000.0);
        Thread.sleep(totalNanos / 1_000_000L, (int) (totalNanos % 1_000_000L));
    }

    private static Object attribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (IllegalAccessException | NoSuchFieldException ignored) {
            try {
                var method = target.getClass().getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private static int readEnvInt(Map<String, String> env, String name, int defaultValue, int minValue) {
        var raw = env.get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            var value = Integer.parseInt(raw.strip());
            return value >= minValue ? value : defaultValue;
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    private static double readEnvFloat(Map<String, String> env, String name, double defaultValue, double minValue) {
        var raw = env.get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            var value = Double.parseDouble(raw.strip());
            return Double.isFinite(value) && value >= minValue ? value : defaultValue;
        } catch (NumberFormatException error) {
            return defaultValue;
        }
    }

    private static String slugify(String provider) {
        if (provider == null) {
            return "";
        }
        var value = provider.strip();
        if ("*".equals(value)) {
            return "*";
        }
        return SLUG_SEPARATOR.matcher(value.toLowerCase(Locale.ROOT))
                .replaceAll("-")
                .replaceAll("^-+|-+$", "");
    }

    private static String codeFromMarkers(String message, Map<String, Set<String>> markers) {
        for (var entry : markers.entrySet()) {
            for (var marker : entry.getValue()) {
                if (message.contains(marker)) {
                    return entry.getKey();
                }
            }
        }
        return "";
    }

    private static Map<String, Set<String>> normalizeMarkers(Map<String, ? extends Iterable<String>> markers) {
        if (markers == null || markers.isEmpty()) {
            return Map.of();
        }
        var normalized = new java.util.LinkedHashMap<String, Set<String>>();
        for (var entry : markers.entrySet()) {
            var values = new LinkedHashSet<String>();
            for (var marker : entry.getValue()) {
                values.add(marker.toLowerCase(Locale.ROOT));
            }
            normalized.put(entry.getKey(), Set.copyOf(values));
        }
        return Map.copyOf(normalized);
    }

    public record RetrySettings(
            int maxAttempts,
            double initialSeconds,
            double expBase,
            double jitterSeconds,
            double capSeconds) {
    }

    public record ErrorPolicy(
            Set<Class<? extends Throwable>> authExceptions,
            Set<Class<? extends Throwable>> rateLimitExceptions,
            Set<Class<? extends Throwable>> networkExceptions,
            Set<Class<? extends Throwable>> httpExceptions,
            Set<String> nonRetryableCodes,
            boolean retry5xx,
            Map<String, Set<String>> messageMarkers) {
        public ErrorPolicy {
            authExceptions = copy(authExceptions);
            rateLimitExceptions = copy(rateLimitExceptions);
            networkExceptions = copy(networkExceptions);
            httpExceptions = copy(httpExceptions);
            nonRetryableCodes = nonRetryableCodes == null ? Set.of() : Set.copyOf(nonRetryableCodes);
            messageMarkers = messageMarkers == null ? Map.of() : Map.copyOf(messageMarkers);
        }

        private static Set<Class<? extends Throwable>> copy(Set<Class<? extends Throwable>> types) {
            return types == null ? Set.of() : Set.copyOf(types);
        }
    }

    public static final class RetryExhaustedException extends RuntimeException {
        private RetryExhaustedException(String provider, int attempts, Throwable cause) {
            super("Retry attempts exhausted for " + slugify(provider) + " after " + attempts + " attempt(s).", cause);
        }
    }
}
