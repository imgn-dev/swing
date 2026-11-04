package be.imgn.swing.internal;

import be.imgn.swing.Task;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A task that runs: its hooks, and whether it has started or belongs to a chain. Either a {@link BackgroundTask}, whose
 * work runs on a thread, or a {@link ChainTask}.
 *
 * @param <T> the type of the result
 */
public abstract sealed class AbstractTask<T extends @Nullable Object> implements Task<T>
        permits BackgroundTask, ChainTask {

    private final List<Consumer<? super T>> onSuccess = new ArrayList<>();
    private final List<Consumer<? super Throwable>> onFailure = new ArrayList<>();
    private final List<Runnable> onCancel = new ArrayList<>();
    private final List<Runnable> onEnd = new ArrayList<>();
    private boolean started;
    private boolean owned;

    AbstractTask() {}

    @Override
    public synchronized Task<T> onSuccess(Consumer<? super T> handler) {
        onSuccess.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public synchronized Task<T> onFailure(Consumer<? super Throwable> handler) {
        onFailure.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public synchronized Task<T> onCancel(Runnable handler) {
        onCancel.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public synchronized Task<T> onEnd(Runnable handler) {
        onEnd.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override
    public synchronized <R extends @Nullable Object> Task<R> then(Function<? super T, ? extends Task<R>> next) {
        Objects.requireNonNull(next, "next");
        if (started) {
            throw new IllegalStateException("The task has started: it can't be chained");
        }
        if (owned) {
            throw new IllegalStateException("The task already belongs to a chain");
        }
        owned = true;
        return new ChainTask<>(this, next);
    }

    /** Marks the task started on its own; throws if it already was, or if it belongs to a chain. */
    synchronized void startAlone() {
        if (owned) {
            throw new IllegalStateException("The task belongs to a chain: run the chain instead");
        }
        if (started) {
            throw new IllegalStateException("The task has already run");
        }
        started = true;
    }

    /** Marks the task started by the chain that owns it, as its first task. */
    synchronized void startInChain() {
        started = true;
    }

    /** Marks the task started by a chain whose {@code next} returned it; throws if it started or belongs to a chain. */
    synchronized void claimForChain() {
        if (started) {
            throw new IllegalStateException("The task that next returned has already started");
        }
        if (owned) {
            throw new IllegalStateException("The task that next returned already belongs to a chain");
        }
        owned = true;
        started = true;
    }

    synchronized List<Consumer<? super T>> successHooks() {
        return List.copyOf(onSuccess);
    }

    synchronized List<Consumer<? super Throwable>> failureHooks() {
        return List.copyOf(onFailure);
    }

    synchronized List<Runnable> cancelHooks() {
        return List.copyOf(onCancel);
    }

    synchronized List<Runnable> endHooks() {
        return List.copyOf(onEnd);
    }
}
