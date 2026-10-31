package be.imgn.swing.internal;

import be.imgn.swing.Task;
import be.imgn.swing.TaskWork;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A task: its work and its hooks.
 *
 * @param <T> the type of the result
 */
public final class BackgroundTask<T extends @Nullable Object> implements Task<T> {

    private final TaskWork<T> work;
    private final List<Consumer<? super T>> onSuccess = new ArrayList<>();
    private final List<Consumer<? super Throwable>> onFailure = new ArrayList<>();
    private final List<Runnable> onCancel = new ArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Creates a task.
     *
     * @param work the work
     */
    public BackgroundTask(TaskWork<T> work) {
        this.work = Objects.requireNonNull(work, "work");
    }

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

    TaskWork<T> work() {
        return work;
    }

    /** Marks the task started; throws if it already was. */
    void start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("The task has already run");
        }
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
}
