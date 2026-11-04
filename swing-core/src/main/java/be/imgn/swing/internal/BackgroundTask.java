package be.imgn.swing.internal;

import be.imgn.swing.PublishingWork;
import be.imgn.swing.TaskWork;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * A task whose work runs on a thread, with its hooks.
 *
 * @param <T> the type of the result
 */
public final class BackgroundTask<T extends @Nullable Object> extends AbstractTask<T> {

    private final @Nullable TaskWork<T> work;
    private final @Nullable Publishing<T, ?> publishing;
    private final boolean delivery;
    private volatile @Nullable Publisher<?> publisher;

    /**
     * Creates a task.
     *
     * @param work the work
     */
    public BackgroundTask(TaskWork<T> work) {
        this(work, false);
    }

    private BackgroundTask(TaskWork<T> work, boolean delivery) {
        this.work = Objects.requireNonNull(work, "work");
        this.publishing = null;
        this.delivery = delivery;
    }

    private BackgroundTask(Publishing<T, ?> publishing) {
        this.work = null;
        this.publishing = publishing;
        this.delivery = false;
    }

    /**
     * Creates the task of an {@code @OnVirtualThread} delivery of the application's bus, which the runner tags as such.
     *
     * @param delivery the delivery, as the bus hands it to the executor
     * @return the task
     */
    static BackgroundTask<@Nullable Void> delivery(Runnable delivery) {
        return new BackgroundTask<>(new RunnableWork(delivery), true);
    }

    /**
     * Creates a task whose work publishes partial results.
     *
     * @param work the work
     * @param onPublish receives the published values, in batches
     * @param <T> the type of the result
     * @param <P> the type of the published values
     * @return the task
     */
    public static <T extends @Nullable Object, P> BackgroundTask<T> publishing(
            PublishingWork<T, P> work, Consumer<? super List<P>> onPublish) {
        return new BackgroundTask<>(
                new Publishing<>(Objects.requireNonNull(work, "work"), Objects.requireNonNull(onPublish, "onPublish")));
    }

    /**
     * Runs the work, on the task's thread.
     *
     * @param task the running task, the work's context
     * @param hooks runs a hook, giving its failure to the failure handler
     * @return the result
     * @throws Exception if the work fails
     */
    T run(RunningTask task, Consumer<Runnable> hooks) throws Exception {
        TaskWork<T> plain = work;
        if (plain != null) {
            return plain.run(task);
        }
        return Objects.requireNonNull(publishing, "publishing").run(this, task, hooks);
    }

    /**
     * Returns whether the task is an {@code @OnVirtualThread} delivery of the application's bus.
     *
     * @return {@code true} for a delivery
     */
    boolean isDelivery() {
        return delivery;
    }

    /** Delivers the values published and not yet delivered, before the hooks of a task that wasn't cancelled. */
    void flushPublished() {
        Publisher<?> current = publisher;
        if (current != null) {
            current.deliver();
        }
    }

    private record Publishing<T extends @Nullable Object, P>(
            PublishingWork<T, P> work, Consumer<? super List<P>> onPublish) {

        T run(BackgroundTask<T> owner, RunningTask task, Consumer<Runnable> hooks) throws Exception {
            Publisher<P> created = new Publisher<>(task, onPublish, hooks);
            owner.publisher = created;
            try {
                return work.run(task, created);
            } finally {
                created.close();
            }
        }
    }
}
