package be.imgn.swing.ui.internal;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.Settling;
import java.awt.EventQueue;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Runs code on the EDT and waits for it, from any thread. */
public final class Edt {

    private Edt() {}

    /**
     * Runs code on the EDT and returns its result: at once on the EDT, else through
     * {@link EventQueue#invokeAndWait(Runnable)}, then lets a {@link Settling} executor settle.
     *
     * @param executor the UI executor
     * @param code the code, which returns a non-null result
     * @param <T> the type of the result
     * @return the result
     */
    public static <T> T call(UiExecutor executor, Supplier<T> code) {
        if (EventQueue.isDispatchThread()) {
            return code.get();
        }
        AtomicReference<T> result = new AtomicReference<>();
        invokeAndWait(() -> result.set(code.get()));
        if (executor instanceof Settling settling) {
            settling.settle();
        }
        return Objects.requireNonNull(result.get());
    }

    /**
     * Runs code on the EDT and waits for it, rethrowing what it throws: at once on the EDT.
     *
     * @param code the code
     * @throws IllegalStateException if the waiting thread is interrupted, or the code throws a checked exception
     */
    public static void invokeAndWait(Runnable code) {
        if (EventQueue.isDispatchThread()) {
            code.run();
            return;
        }
        try {
            EventQueue.invokeAndWait(code);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the EDT", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }
}
