package be.imgn.swing.internal;

import org.jspecify.annotations.Nullable;

/**
 * Runs code whose failure must not stop the code around it: a task's hooks and a chain's next step, which catch an
 * {@link Error} too, and a command handler, a dynamic item's action, a converter, a rule or a periodic action, which
 * let one propagate.
 */
public final class Guarded {

    private Guarded() {}

    /**
     * Runs code and returns what it threw.
     *
     * @param code the code
     * @return what the code threw, or {@code null} if it returned
     */
    // Whatever a hook throws goes to the FailureHandler, and the hooks after it still run.
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static @Nullable Throwable run(Runnable code) {
        try {
            code.run();
            return null;
        } catch (Exception | Error e) {
            return e;
        }
    }

    /**
     * Runs code and returns the exception it threw; an {@link Error} propagates.
     *
     * @param code the code
     * @return the exception the code threw, or {@code null} if it returned
     */
    // Whatever exception the code throws goes to the FailureHandler; only tasks catch an Error.
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static @Nullable Exception runCatchingExceptions(Runnable code) {
        try {
            code.run();
            return null;
        } catch (Exception e) {
            return e;
        }
    }
}
