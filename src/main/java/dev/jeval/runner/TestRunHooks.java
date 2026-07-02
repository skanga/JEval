package dev.jeval.runner;

import java.util.concurrent.atomic.AtomicReference;

public final class TestRunHooks {
    private static final AtomicReference<Runnable> ON_TEST_RUN_END = new AtomicReference<>();

    private TestRunHooks() {
    }

    public static Runnable onTestRunEnd(Runnable hook) {
        ON_TEST_RUN_END.set(hook);
        return hook;
    }

    public static void invokeTestRunEndHook() {
        var hook = ON_TEST_RUN_END.getAndSet(null);
        if (hook != null) {
            hook.run();
        }
    }
}
