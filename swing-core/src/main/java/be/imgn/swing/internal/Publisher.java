package be.imgn.swing.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Publishes the partial results of a task: the values published from any thread are delivered through the
 * {@link be.imgn.swing.UiExecutor} in batches, until the task is cancelled.
 *
 * @param <P> the type of the published values
 */
final class Publisher<P> implements Consumer<P> {

    private final RunningTask task;
    private final Consumer<? super List<P>> onPublish;
    private final Consumer<Runnable> hooks;
    private final List<P> pending = new ArrayList<>();
    private boolean scheduled;
    private boolean closed;

    /**
     * Creates a publisher.
     *
     * @param task the running task
     * @param onPublish receives the batches
     * @param hooks runs a hook, giving its failure to the failure handler
     */
    Publisher(RunningTask task, Consumer<? super List<P>> onPublish, Consumer<Runnable> hooks) {
        this.task = task;
        this.onPublish = onPublish;
        this.hooks = hooks;
    }

    @Override
    public void accept(P value) {
        Objects.requireNonNull(value, "value");
        boolean schedule;
        synchronized (this) {
            if (closed) {
                throw new IllegalStateException("The work has returned: it can't publish any more");
            }
            if (task.isCancelled()) {
                return;
            }
            pending.add(value);
            schedule = !scheduled;
            scheduled = true;
        }
        if (schedule) {
            task.executor().execute(this::deliver);
        }
    }

    /** Refuses any later value: the work has returned or thrown. */
    synchronized void close() {
        closed = true;
    }

    /** Delivers the values published since the previous delivery, unless the task is cancelled. */
    void deliver() {
        List<P> batch;
        synchronized (this) {
            scheduled = false;
            if (task.isCancelled()) {
                pending.clear();
                return;
            }
            if (pending.isEmpty()) {
                return;
            }
            batch = List.copyOf(pending);
            pending.clear();
        }
        hooks.accept(() -> onPublish.accept(batch));
    }
}
