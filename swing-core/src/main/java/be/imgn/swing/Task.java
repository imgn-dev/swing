package be.imgn.swing;

import be.imgn.swing.internal.BackgroundTask;
import be.imgn.swing.internal.RunnableWork;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Work that runs on a virtual thread, with its completion hooks.
 *
 * <p>The hooks, the progress and the messages are delivered through the {@link UiExecutor}: on the EDT with the UI
 * module. Cancelling interrupts the thread; a task that ends by {@link InterruptedException} or
 * {@link java.util.concurrent.CancellationException} after cancellation runs its {@link #onCancel(Runnable)} hooks. A
 * failure with no {@link #onFailure(Consumer)} hook goes to the {@link FailureHandler}. A task belongs to the scope it
 * was started in: closing the scope cancels it.
 *
 * <p>Run a task with {@link TaskService#run(Task, Scope)}, or return it from a command handler, which runs it.
 *
 * @param <T> the type of the result
 */
public interface Task<T extends @Nullable Object> {

    /**
     * Returns a task of work that returns a result.
     *
     * @param work the work
     * @param <T> the type of the result
     * @return the task
     */
    static <T extends @Nullable Object> Task<T> of(Callable<T> work) {
        return new BackgroundTask<T>(context -> work.call());
    }

    /**
     * Returns a task of work with no result.
     *
     * @param work the work
     * @return the task
     */
    static Task<@Nullable Void> of(Runnable work) {
        return new BackgroundTask<>(new RunnableWork(work));
    }

    /**
     * Returns a task of work that reports its progress or checks whether it is cancelled.
     *
     * @param work the work, given the task's context
     * @param <T> the type of the result
     * @return the task
     */
    static <T extends @Nullable Object> Task<T> of(TaskWork<T> work) {
        return new BackgroundTask<>(work);
    }

    /**
     * Adds a hook called with the result when the work succeeds.
     *
     * @param handler the hook
     * @return this task
     */
    Task<T> onSuccess(Consumer<? super T> handler);

    /**
     * Adds a hook called with the failure when the work fails.
     *
     * @param handler the hook
     * @return this task
     */
    Task<T> onFailure(Consumer<? super Throwable> handler);

    /**
     * Adds a hook called when the work ends after cancellation.
     *
     * @param handler the hook
     * @return this task
     */
    Task<T> onCancel(Runnable handler);
}
