package dev.jeval.models;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public final class RetryPolicy {
    private static final String SDK_RETRY_PROVIDERS = "DEEPEVAL_SDK_RETRY_PROVIDERS";
    private static final double DEFAULT_TASK_TIMEOUT_SECONDS = 180.0;
    private static final double TIMEOUT_SAFETY_SECONDS = 1.0;
    private static final ThreadLocal<Long> OUTER_DEADLINE_NANOS = new ThreadLocal<>();
    private static final ConcurrentHashMap<Integer, Semaphore> TIMEOUT_LIMITERS = new ConcurrentHashMap<>();
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

    public static TimeoutSettings timeoutSettings() {
        return timeoutSettings(System.getenv());
    }

    static TimeoutSettings timeoutSettings(Map<String, String> env) {
        return timeoutSettings(env, retrySettings(env));
    }

    static TimeoutSettings timeoutSettings(Map<String, String> env, RetrySettings retrySettings) {
        var perAttemptOverride = readEnvFloat(env, "DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS_OVERRIDE", 0.0, 0.0);
        if (perAttemptOverride == 0.0) {
            perAttemptOverride = readEnvFloat(env, "DEEPEVAL_PER_ATTEMPT_TIMEOUT_SECONDS", 0.0, 0.0);
        }
        var perTaskOverride = readEnvFloat(env, "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS_OVERRIDE", 0.0, 0.0);
        if (perTaskOverride == 0.0) {
            perTaskOverride = readEnvFloat(env, "DEEPEVAL_PER_TASK_TIMEOUT_SECONDS", 0.0, 0.0);
        }
        var perTask = perTaskOverride > 0.0
                ? perTaskOverride
                : autoTaskTimeoutSeconds(retrySettings, perAttemptOverride);
        var perAttempt = perAttemptOverride > 0.0
                ? perAttemptOverride
                : computedAttemptTimeoutSeconds(retrySettings, perTaskOverride);
        var gatherBuffer = readEnvFloat(env, "DEEPEVAL_TASK_GATHER_BUFFER_SECONDS_OVERRIDE", -1.0, 0.0);
        if (gatherBuffer < 0.0) {
            gatherBuffer = readEnvFloat(env, "DEEPEVAL_TASK_GATHER_BUFFER_SECONDS", -1.0, 0.0);
        }
        if (gatherBuffer < 0.0) {
            gatherBuffer = constrain(0.15 * perTask, 10.0, 60.0);
        }
        return new TimeoutSettings(
                readEnvBoolean(env, "DEEPEVAL_DISABLE_TIMEOUTS", false),
                perAttempt,
                perTask,
                gatherBuffer,
                readEnvInt(env, "DEEPEVAL_TIMEOUT_THREAD_LIMIT", 128, 1),
                readEnvFloat(env, "DEEPEVAL_TIMEOUT_SEMAPHORE_WARN_AFTER_SECONDS", 5.0, 0.0));
    }

    public static OuterDeadlineToken setOuterDeadlineSeconds(double seconds, TimeoutSettings settings) {
        var previous = OUTER_DEADLINE_NANOS.get();
        if (!settings.disabled() && seconds > 0.0) {
            var deadline = System.nanoTime() + Math.round(seconds * 1_000_000_000.0);
            OUTER_DEADLINE_NANOS.set(deadline);
        } else {
            OUTER_DEADLINE_NANOS.remove();
        }
        return new OuterDeadlineToken(previous);
    }

    public static Double remainingBudgetSeconds() {
        var deadline = OUTER_DEADLINE_NANOS.get();
        if (deadline == null) {
            return null;
        }
        return Math.max(0.0, (deadline - System.nanoTime()) / 1_000_000_000.0);
    }

    public static boolean isBudgetSpent() {
        var remaining = remainingBudgetSeconds();
        return remaining != null && remaining <= 0.0;
    }

    public static double resolveEffectiveAttemptTimeoutSeconds(TimeoutSettings settings) {
        if (settings.disabled() || settings.perAttemptSeconds() <= 0.0) {
            return 0.0;
        }
        var remaining = remainingBudgetSeconds();
        if (remaining != null) {
            return Math.max(0.0, Math.min(settings.perAttemptSeconds(), remaining));
        }
        return settings.perAttemptSeconds();
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
        return executeWithRetry(provider, operation, policies, sdkRetryProviders(), retrySettings(), timeoutSettings());
    }

    public static <T> T executeWithRetry(
            String provider,
            Callable<T> operation,
            Map<String, ErrorPolicy> policies,
            Iterable<?> sdkRetryProviders,
            RetrySettings settings) throws Exception {
        return executeWithRetry(
                provider,
                operation,
                policies,
                sdkRetryProviders,
                settings,
                new TimeoutSettings(true, 0.0));
    }

    public static <T> T executeWithRetry(
            String provider,
            Callable<T> operation,
            Map<String, ErrorPolicy> policies,
            Iterable<?> sdkRetryProviders,
            RetrySettings settings,
            TimeoutSettings timeoutSettings) throws Exception {
        var policy = getRetryPolicyFor(provider, policies, sdkRetryProviders);
        if (policy == null) {
            return callWithTimeout(operation, timeoutSettings);
        }

        var attempt = 1;
        while (true) {
            try {
                return callWithTimeout(operation, timeoutSettings);
            } catch (Exception error) {
                if (!isRetryable(policy, error)) {
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

    private static boolean isRetryable(ErrorPolicy policy, Throwable error) {
        return error instanceof RetryTimeoutException || isTransient(policy, error);
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

    private static <T> T callWithTimeout(Callable<T> operation, TimeoutSettings settings) throws Exception {
        if (isBudgetSpent()) {
            throw new RetryTimeoutException(0.0, null);
        }
        var timeoutSeconds = resolveEffectiveAttemptTimeoutSeconds(settings);
        if (timeoutSeconds <= 0.0) {
            return operation.call();
        }
        var limiter = TIMEOUT_LIMITERS.computeIfAbsent(settings.threadLimit(), Semaphore::new);
        acquireTimeoutPermit(limiter, settings);
        var executor = Executors.newSingleThreadExecutor(task -> {
            var thread = new Thread(task, "jeval-timeout-worker");
            thread.setDaemon(true);
            return thread;
        });
        var future = executor.submit(() -> {
            try {
                return operation.call();
            } finally {
                limiter.release();
            }
        });
        try {
            return future.get(
                    Math.round(timeoutSeconds * 1_000_000_000.0),
                    TimeUnit.NANOSECONDS);
        } catch (TimeoutException error) {
            future.cancel(true);
            throw new RetryTimeoutException(timeoutSeconds, error);
        } catch (ExecutionException error) {
            var cause = error.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException(cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void acquireTimeoutPermit(Semaphore limiter, TimeoutSettings settings) throws InterruptedException {
        if (settings.semaphoreWarnAfterSeconds() > 0.0) {
            var acquired = limiter.tryAcquire(
                    Math.round(settings.semaphoreWarnAfterSeconds() * 1_000_000_000.0),
                    TimeUnit.NANOSECONDS);
            if (acquired) {
                return;
            }
        }
        limiter.acquire();
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

    private static boolean readEnvBoolean(Map<String, String> env, String name, boolean defaultValue) {
        var raw = env.get(name);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Set.of("1", "true", "yes", "y", "on").contains(raw.strip().toLowerCase(Locale.ROOT));
    }

    private static double computedAttemptTimeoutSeconds(RetrySettings retrySettings, double perTaskOverride) {
        var attempts = retrySettings.maxAttempts();
        var outer = perTaskOverride > 0.0 ? perTaskOverride : DEFAULT_TASK_TIMEOUT_SECONDS;
        var usable = Math.max(0.0, outer - expectedBackoffSeconds(retrySettings) - TIMEOUT_SAFETY_SECONDS);
        if (usable <= 0.0) {
            return 0.0;
        }
        var timeout = usable / attempts;
        return perTaskOverride > 0.0 ? timeout : Math.max(1.0, timeout);
    }

    private static double autoTaskTimeoutSeconds(RetrySettings retrySettings, double perAttemptOverride) {
        if (perAttemptOverride <= 0.0) {
            return DEFAULT_TASK_TIMEOUT_SECONDS;
        }
        return Math.ceil(retrySettings.maxAttempts() * perAttemptOverride
                + expectedBackoffSeconds(retrySettings)
                + TIMEOUT_SAFETY_SECONDS);
    }

    private static double expectedBackoffSeconds(RetrySettings retrySettings) {
        var sleeps = Math.max(0, retrySettings.maxAttempts() - 1);
        var current = retrySettings.initialSeconds();
        var backoff = 0.0;
        for (var i = 0; i < sleeps; i++) {
            backoff += Math.min(retrySettings.capSeconds(), current);
            current *= retrySettings.expBase();
        }
        return backoff + sleeps * (retrySettings.jitterSeconds() / 2.0);
    }

    private static double constrain(double value, double minimum, double maximum) {
        return Math.min(Math.max(value, minimum), maximum);
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

    public record TimeoutSettings(
            boolean disabled,
            double perAttemptSeconds,
            double perTaskSeconds,
            double gatherBufferSeconds,
            int threadLimit,
            double semaphoreWarnAfterSeconds) {
        public TimeoutSettings(boolean disabled, double perAttemptSeconds) {
            this(disabled, perAttemptSeconds, 0.0, 0.0);
        }

        public TimeoutSettings(
                boolean disabled,
                double perAttemptSeconds,
                double perTaskSeconds,
                double gatherBufferSeconds) {
            this(disabled, perAttemptSeconds, perTaskSeconds, gatherBufferSeconds, 128, 5.0);
        }
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

    public static final class RetryTimeoutException extends RuntimeException {
        private RetryTimeoutException(double seconds, Throwable cause) {
            super("call timed out after " + seconds + "s (per attempt).", cause);
        }
    }

    public static final class OuterDeadlineToken implements AutoCloseable {
        private final Long previousDeadlineNanos;
        private boolean closed;

        private OuterDeadlineToken(Long previousDeadlineNanos) {
            this.previousDeadlineNanos = previousDeadlineNanos;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (previousDeadlineNanos == null) {
                OUTER_DEADLINE_NANOS.remove();
            } else {
                OUTER_DEADLINE_NANOS.set(previousDeadlineNanos);
            }
            closed = true;
        }
    }
}
