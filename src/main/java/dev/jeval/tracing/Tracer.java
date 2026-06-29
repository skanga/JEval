package dev.jeval.tracing;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class Tracer {
    private final ArrayDeque<Span> stack = new ArrayDeque<>();
    private Span root;

    public <T> T observe(String name, Supplier<T> supplier) {
        return observe(name, "span", Map.of(), supplier);
    }

    public <T> T observe(String name, String type, Map<String, Object> metadata, Supplier<T> supplier) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' must not be blank");
        }
        if (supplier == null) {
            throw new IllegalArgumentException("'supplier' must not be null");
        }
        var span = new Span(name, type == null || type.isBlank() ? "span" : type, metadata);
        start(span);
        try {
            var output = supplier.get();
            span.output = output;
            return output;
        } catch (RuntimeException | Error error) {
            span.error = error;
            throw error;
        } finally {
            span.finish();
            stack.pop();
        }
    }

    public Object invokeObserved(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            throw new IllegalArgumentException("'target' must not be null");
        }
        try {
            var method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
            var observe = method.getAnnotation(Observe.class);
            var name = observe == null || observe.name().isBlank() ? method.getName() : observe.name();
            var type = observe == null ? "span" : observe.type();
            method.setAccessible(true);
            return observe(name, type, Map.of(), () -> {
                try {
                    return method.invoke(target, args);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (InvocationTargetException e) {
                    throw rethrow(e.getCause());
                }
            });
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Observed method not found: " + methodName, e);
        }
    }

    public Map<String, Object> trace() {
        if (root == null) {
            throw new IllegalStateException("No trace has been captured");
        }
        return root.toMap();
    }

    public void clear() {
        stack.clear();
        root = null;
    }

    private void start(Span span) {
        if (stack.isEmpty()) {
            root = span;
        } else {
            stack.peek().children.add(span);
        }
        stack.push(span);
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException(cause);
    }

    private static final class Span {
        private final String name;
        private final String type;
        private final Map<String, Object> metadata;
        private final long startedAtNanos = System.nanoTime();
        private final List<Span> children = new ArrayList<>();
        private Object output;
        private Throwable error;
        private Long durationMillis;

        private Span(String name, String type, Map<String, Object> metadata) {
            this.name = name;
            this.type = type;
            this.metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        private void finish() {
            durationMillis = Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000_000);
        }

        private Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("name", name);
            map.put("type", type);
            map.put("metadata", metadata);
            map.put("output", output);
            map.put("error", error == null ? null : errorMap(error));
            map.put("duration_millis", durationMillis);
            map.put("children", children.stream().map(Span::toMap).toList());
            return Collections.unmodifiableMap(map);
        }

        private static Map<String, Object> errorMap(Throwable error) {
            var map = new LinkedHashMap<String, Object>();
            map.put("type", error.getClass().getName());
            map.put("message", error.getMessage());
            return Collections.unmodifiableMap(map);
        }
    }
}
