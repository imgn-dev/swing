package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.UiExecutor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/** Runs tasks on virtual threads, and keeps track of those running. */
@Singleton
public final class TaskRunner {

    private final UiExecutor executor;
    private final FailureHandler failures;
    private final Set<RunningTask> running = ConcurrentHashMap.newKeySet();

    @Inject
    TaskRunner(UiExecutor executor, FailureHandler failures) {
        this.executor = executor;
        this.failures = failures;
    }

    /**
     * Starts a task in a scope, which cancels it when it closes.
     *
     * @param task the task
     * @param scope the scope
     * @param name the name of the task's thread
     * @return the running task
     * @throws IllegalStateException if the task has already run, or the scope is closed
     */
    public RunningTask start(Task<?> task, Scope scope, String name) {
        return start(task, scope, name, false);
    }

    /**
     * Starts a task in a scope, which cancels it when it closes.
     *
     * @param task the task
     * @param scope the scope
     * @param name the name of the task's thread
     * @param command whether a command starts the task
     * @return the running task
     * @throws IllegalStateException if the task has already run, or the scope is closed
     */
    public RunningTask start(Task<?> task, Scope scope, String name, boolean command) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(name, "name");
        if (scope.isClosed()) {
            throw new IllegalStateException("Cannot start a task in " + scope + ": it is closed");
        }
        BackgroundTask<?> background = (BackgroundTask<?>) task;
        background.start();
        RunningTask handle = new RunningTask(name, command, executor);
        Runnable withdraw =
                scope instanceof DisposalScope disposal ? disposal.register(handle::cancel) : register(scope, handle);
        running.add(handle);
        Thread thread = Thread.ofVirtual().name(name).unstarted(() -> run(background, handle, withdraw));
        handle.started(thread);
        thread.start();
        return handle;
    }

    private static Runnable register(Scope scope, RunningTask handle) {
        scope.onClose(handle::cancel);
        return () -> {};
    }

    /**
     * Returns the tasks that are running.
     *
     * @return the running tasks
     */
    public List<RunningTask> running() {
        return List.copyOf(running);
    }

    /**
     * Cancels every running task, and waits for them to end.
     *
     * @param timeout how long to wait at most, for all of them
     */
    public void cancelAndAwait(Duration timeout) {
        List<RunningTask> tasks = running();
        tasks.forEach(RunningTask::cancel);
        Instant deadline = Instant.now().plus(timeout);
        try {
            for (RunningTask task : tasks) {
                Duration left = Duration.between(Instant.now(), deadline);
                if (left.isNegative() || !task.await(left)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private <T extends @Nullable Object> void run(BackgroundTask<T> task, RunningTask handle, Runnable withdraw) {
        try {
            T result = task.work().run(handle);
            executor.execute(() -> succeeded(task, result));
        } catch (InterruptedException | CancellationException e) {
            if (handle.isCancelled()) {
                executor.execute(() -> task.cancelHooks().forEach(Runnable::run));
            } else {
                executor.execute(() -> failed(task, e));
            }
        } catch (Exception | Error e) {
            executor.execute(() -> failed(task, e));
        } finally {
            running.remove(handle);
            withdraw.run();
            handle.ended();
        }
    }

    private <T extends @Nullable Object> void succeeded(BackgroundTask<T> task, T result) {
        for (Consumer<? super T> hook : task.successHooks()) {
            hook.accept(result);
        }
    }

    private void failed(BackgroundTask<?> task, Throwable failure) {
        List<Consumer<? super Throwable>> hooks = task.failureHooks();
        if (hooks.isEmpty()) {
            failures.failed(failure);
            return;
        }
        hooks.forEach(hook -> hook.accept(failure));
    }
}
