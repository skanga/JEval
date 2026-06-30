package dev.jeval.models;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class RetryPolicy {
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
}
