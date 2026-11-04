package be.imgn.swing.internal;

import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.TaskContext;
import be.imgn.swing.UiExecutor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * A task that was given to the runner, a chain included: its threads, its progress and message, and its cancellation.
 * It may be prepared before it begins, as a task that waits for its turn: until it begins, it holds no thread.
 */
public final class RunningTask implements TaskContext {

    private static final double INDETERMINATE = -1;

    private final String name;
    private final boolean command;
    private final boolean delivery;
    private final UiExecutor executor;
    private final Property<Double> progress = new AtomicProperty<>(INDETERMINATE);
    private final Property<String> message = new AtomicProperty<>("");
    private final List<Runnable> endListeners = new ArrayList<>();
    private volatile boolean cancelled;
    private @Nullable Thread thread;
    private CountDownLatch step = new CountDownLatch(0);
    private @Nullable Runnable starter;
    private boolean begun;
    private boolean stepped;
    private boolean ended;

    RunningTask(String name, boolean command, boolean delivery, UiExecutor executor) {
        this.name = name;
        this.command = command;
        this.delivery = delivery;
        this.executor = executor;
    }

    /**
     * Returns whether a command started the task, whose activation waits for it in the test harness.
     *
     * @return {@code true} for the task of a command
     */
    public boolean isCommand() {
        return command;
    }

    /**
     * Returns whether the task runs an {@code @OnVirtualThread} delivery of the application's bus, which the test
     * harness waits for.
     *
     * @return {@code true} for a delivery
     */
    public boolean isDelivery() {
        return delivery;
    }

    /**
     * Returns the name of the task's threads.
     *
     * @return the name, the command's id or {@code task}
     */
    public String name() {
        return name;
    }

    /**
     * Returns the progress of the step that runs, updated on the {@link UiExecutor}'s thread.
     *
     * @return the fraction done, or -1 while indeterminate
     */
    public Observable<Double> progressValue() {
        return progress;
    }

    /**
     * Returns the message of the step that runs, updated on the {@link UiExecutor}'s thread.
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

    /**
     * Cancels the task: interrupts the thread of the step that runs. A task that hasn't begun begins at once,
     * cancelled, so that it starts no work and runs its cancel and end hooks. Idempotent.
     */
    public void cancel() {
        synchronized (this) {
            cancelled = true;
            Thread current = thread;
            if (current != null) {
                current.interrupt();
            }
        }
        begin();
    }

    /** Begins the task, if it hasn't begun: starts its first step, or, once cancelled, only runs its hooks. */
    public void begin() {
        Runnable start;
        synchronized (this) {
            if (begun || starter == null) {
                return;
            }
            begun = true;
            start = starter;
            starter = null;
        }
        start.run();
    }

    /**
     * Returns whether the task has begun.
     *
     * @return {@code true} once begun, cancelled included
     */
    public synchronized boolean isBegun() {
        return begun;
    }

    /**
     * Returns whether the task has ended: its last hook has run.
     *
     * @return {@code true} once ended
     */
    public synchronized boolean isDone() {
        return ended;
    }

    /**
     * Waits for the work of the step that runs to end; returns at once when no step runs, such as between the steps of
     * a chain, or before the task begins.
     *
     * @param timeout how long to wait at most
     * @return {@code true} if the work ended in time
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public boolean await(Duration timeout) throws InterruptedException {
        CountDownLatch current;
        synchronized (this) {
            current = step;
        }
        return current.await(timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    /**
     * Runs code once the task has ended, on the {@link UiExecutor}'s thread, right after its last hook; at once if it
     * has ended.
     *
     * @param listener the code
     */
    public void whenEnded(Runnable listener) {
        synchronized (this) {
            if (!ended) {
                endListeners.add(listener);
                return;
            }
        }
        listener.run();
    }

    UiExecutor executor() {
        return executor;
    }

    synchronized void starter(Runnable start) {
        starter = start;
    }

    /**
     * Records the thread of a step about to start. A step after the first shows its own progress and message, which it
     * hasn't reported yet.
     */
    void stepStarting(Thread started) {
        boolean next;
        synchronized (this) {
            thread = started;
            step = new CountDownLatch(1);
            next = stepped;
            stepped = true;
            if (cancelled) {
                started.interrupt();
            }
        }
        if (next) {
            progress.set(INDETERMINATE);
            message.set("");
        }
    }

    /**
     * Returns the latch of the step that runs, which its end counts down: called by the step's thread.
     *
     * @return the latch
     */
    synchronized CountDownLatch currentStep() {
        return step;
    }

    /** Ends a step, on its thread, once its outcome was handed to the {@link UiExecutor}. */
    void stepEnded(CountDownLatch latch) {
        synchronized (this) {
            if (thread == Thread.currentThread()) {
                thread = null;
            }
        }
        latch.countDown();
    }

    void ended() {
        List<Runnable> listeners;
        synchronized (this) {
            ended = true;
            listeners = List.copyOf(endListeners);
            endListeners.clear();
        }
        listeners.forEach(Runnable::run);
    }
}
