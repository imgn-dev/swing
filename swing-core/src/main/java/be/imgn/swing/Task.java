package be.imgn.swing;

import be.imgn.swing.internal.AbstractTask;
import be.imgn.swing.internal.BackgroundTask;
import be.imgn.swing.internal.NoTask;
import be.imgn.swing.internal.RunnableWork;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Work that runs on a virtual thread, with its completion hooks.
 *
 * <p>The hooks, the progress and the messages are delivered through the {@link UiExecutor}: on the EDT with the UI
 * module. Cancelling interrupts the thread; a task cancelled before its hooks run runs its {@link #onCancel(Runnable)}
 * hooks, then its {@link #onEnd(Runnable)} hooks, and never its {@link #onSuccess(Consumer)} or
 * {@link #onFailure(Consumer)} hooks, even when its work had already returned or thrown. A failure with no
 * {@link #onFailure(Consumer)} hook goes to the {@link FailureHandler}, as does a hook that throws; the remaining hooks
 * still run. A task belongs to the scope it was started in: closing the scope cancels it.
 *
 * <p>Run a task with {@link TaskService#run(Task, Scope)}, or return it from a command handler, which runs it. A
 * handler that has nothing to run, such as one whose confirmation was declined, returns {@link #none()}.
 *
 * <p>Only core implements tasks: the interface is sealed, and its static methods and {@link #then(Function)} make them.
 *
 * @param <T> the type of the result
 */
public sealed interface Task<T extends @Nullable Object> permits AbstractTask, NoTask {

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
     * Returns a task of work that publishes partial results while it runs. {@code onPublish} receives the published
     * values through the {@link UiExecutor}, in order, in batches: each call holds the values published since the
     * previous one. Every value published before the work returns or throws reaches {@code onPublish} before the
     * {@link #onSuccess(Consumer)} or {@link #onFailure(Consumer)} hooks run. Once the task is cancelled, the values
     * not yet delivered are dropped, and {@code onPublish} isn't called again.
     *
     * @param work the work, given the task's context and the function that publishes a value
     * @param onPublish receives the published values, in batches
     * @param <T> the type of the result
     * @param <P> the type of the published values
     * @return the task
     * @throws NullPointerException if {@code work} or {@code onPublish} is {@code null}
     */
    static <T extends @Nullable Object, P> Task<T> of(PublishingWork<T, P> work, Consumer<? super List<P>> onPublish) {
        return BackgroundTask.publishing(work, onPublish);
    }

    /**
     * Returns the task with no work, which a command handler returns when it has nothing to run. Running it does
     * nothing: no thread starts, no input is blocked, and its hooks never run. It may be run any number of times. It is
     * one shared instance, which no chain owns: {@code Task.none().then(next)} returns it, without calling
     * {@code next}.
     *
     * @param <T> the type of the result
     * @return the task with no work
     */
    @SuppressWarnings("unchecked")
    static <T extends @Nullable Object> Task<T> none() {
        return (Task<T>) (Task<?>) NoTask.INSTANCE;
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
     * Adds a hook called when the task is cancelled before its hooks run, even when its work had already returned or
     * thrown: the {@link #onSuccess(Consumer)} and {@link #onFailure(Consumer)} hooks then never run.
     *
     * @param handler the hook
     * @return this task
     */
    Task<T> onCancel(Runnable handler);

    /**
     * Adds a hook called through the {@link UiExecutor} after the {@link #onSuccess(Consumer)},
     * {@link #onFailure(Consumer)} or {@link #onCancel(Runnable)} hooks, however the task ended.
     *
     * @param handler the hook
     * @return this task
     * @throws NullPointerException if {@code handler} is {@code null}
     */
    Task<T> onEnd(Runnable handler);

    /**
     * Returns a chain that runs this task and, when it succeeds, calls {@code next} with its result through the
     * {@link UiExecutor}, after this task's own {@link #onSuccess(Consumer)} hooks, then runs the task {@code next}
     * returns, in the same scope.
     *
     * <p>The chain succeeds with the second task's result, or with {@code null} when {@code next} returns
     * {@link #none()}. It fails or is cancelled when either task fails or is cancelled; once this task has failed or
     * been cancelled, {@code next} isn't called. Cancelling the chain cancels whichever task runs. {@code next}
     * throwing fails the chain with that exception; {@code next} returning {@code null} fails it with a
     * {@link NullPointerException}, and returning a task that has started or that belongs to a chain fails it with an
     * {@link IllegalStateException}.
     *
     * <p>This task's own hooks still run, for it alone; the chain's hooks run once, for the chain. A failure reaches
     * the {@link FailureHandler} once, and only when neither the failing task nor the chain has an
     * {@link #onFailure(Consumer)} hook. This task belongs to the chain: running it on its own throws
     * {@link IllegalStateException}.
     *
     * @param next returns the task to run with this task's result
     * @param <R> the type of the chain's result
     * @return the chain
     * @throws NullPointerException if {@code next} is {@code null}
     * @throws IllegalStateException if this task has started, or already belongs to a chain
     */
    <R extends @Nullable Object> Task<R> then(Function<? super T, ? extends Task<R>> next);
}
