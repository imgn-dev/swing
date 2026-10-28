package be.imgn.swing.internal;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.TaskContext;
import be.imgn.swing.UiExecutor;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/** A task that was started: its thread, its progress and message, and its cancellation. */
public final class RunningTask implements TaskContext {

    private static final double INDETERMINATE = -1;

    private final String name;
    private final UiExecutor executor;
    private final Property<Double> progress = new AtomicProperty<>(INDETERMINATE);
    private final Property<String> message = new AtomicProperty<>("");
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean cancelled;
    private volatile @Nullable Thread thread;

    RunningTask(String name, UiExecutor executor) {
        this.name = name;
        this.executor = executor;
    }

    /**
     * Returns the name of the task's thread.
     *
     * @return the name, the command's id or {@code task}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the progress, updated on the {@link UiExecutor}'s thread.
     *
     * @return the fraction done, or -1 while indeterminate
     */
    public Observable<Double> progressValue() {
        return progress;
    }

    /**
     * Returns the message, updated on the {@link UiExecutor}'s thread.
     *
     * @return the last message, empty at first
     */
    public Observable<String> messageValue() {
        return message;
    }

    @Override
    public void progress(double fraction) {
        if (fraction != INDETERMINATE && !(fraction >= 0 && fraction <= 1)) {
            throw new IllegalArgumentException("The progress must be between 0 and 1, or -1: " + fraction);
        }
        executor.execute(() -> progress.set(fraction));
    }

    @Override
    public void message(String text) {
        executor.execute(() -> message.set(text));
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    /** Cancels the task: interrupts its thread. Idempotent. */
    public void cancel() {
        cancelled = true;
        Thread current = thread;
        if (current != null) {
            current.interrupt();
        }
    }

    /**
     * Returns whether the task's work has ended.
     *
     * @return {@code true} once the work has ended
     */
    public boolean isDone() {
        return done.getCount() == 0;
    }

    /**
     * Waits for the task's work to end.
     *
     * @param timeout how long to wait at most
     * @return {@code true} if the work ended in time
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public boolean await(Duration timeout) throws InterruptedException {
        return done.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    void started(Thread started) {
        thread = started;
        if (cancelled) {
            started.interrupt();
        }
    }

    void ended() {
        done.countDown();
    }
}
