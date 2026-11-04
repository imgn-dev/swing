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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Runs tasks, chains included, on virtual threads, and keeps track of those that haven't ended.
 *
 * <p>Each step of a task runs on a thread of its own; its outcome is decided on the {@link UiExecutor}'s thread, when
 * its hooks run: a task cancelled by then runs its cancel hooks, whatever its work did.
 */
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
     * @param name the name of the task's threads
     * @return the running task
     * @throws IllegalStateException if the task has already run or belongs to a chain, or the scope is closed
     */
    public RunningTask start(Task<?> task, Scope scope, String name) {
        return start(task, scope, name, false);
    }

    /**
     * Starts a task in a scope, which cancels it when it closes.
     *
     * @param task the task
     * @param scope the scope
     * @param name the name of the task's threads
     * @param command whether a command starts the task
     * @return the running task
     * @throws IllegalStateException if the task has already run or belongs to a chain, or the scope is closed
     */
    public RunningTask start(Task<?> task, Scope scope, String name, boolean command) {
        RunningTask handle = prepare(task, scope, name, command);
        handle.begin();
        return handle;
    }

    /**
     * Prepares a task in a scope, which cancels it when it closes, without beginning it: it holds no thread until
     * {@link RunningTask#begin()}. Cancelled before, it never starts its work, and runs its cancel and end hooks.
     *
     * @param task the task
     * @param scope the scope
     * @param name the name of the task's threads
     * @param command whether a command starts the task
     * @return the task, not begun
     * @throws IllegalStateException if the task has already run or belongs to a chain, or the scope is closed
     */
    public RunningTask prepare(Task<?> task, Scope scope, String name, boolean command) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(name, "name");
        if (scope.isClosed()) {
            throw new IllegalStateException("Cannot start a task in " + scope + ": it is closed");
        }
        AbstractTask<?> root = (AbstractTask<?>) task;
        root.startAlone();
        boolean delivery = root instanceof BackgroundTask<?> background && background.isDelivery();
        RunningTask handle = new RunningTask(name, command, delivery, executor);
        Runnable withdraw =
                scope instanceof DisposalScope disposal ? disposal.register(handle::cancel) : register(scope, handle);
        running.add(handle);
        handle.starter(() -> runRoot(root, handle, withdraw));
        return handle;
    }

    private static Runnable register(Scope scope, RunningTask handle) {
        scope.onClose(handle::cancel);
        return () -> {};
    }

    /**
     * Returns the tasks that haven't ended, those that haven't begun included.
     *
     * @return the tasks
     */
    public List<RunningTask> running() {
        return List.copyOf(running);
    }

    /**
     * Cancels every task that hasn't ended, and waits for the work of their running steps to end.
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

    private <T extends @Nullable Object> void runRoot(AbstractTask<T> task, RunningTask handle, Runnable withdraw) {
        run(task, handle, true, outcome -> {
            running.remove(handle);
            withdraw.run();
            handle.ended();
        });
    }

    // Runs a task, and once it has ended, its own hooks included, hands its outcome to done, on the executor's thread.
    private <T extends @Nullable Object> void run(
            AbstractTask<T> task, RunningTask handle, boolean root, Consumer<Outcome<T>> done) {
        switch (task) {
            case BackgroundTask<T> background -> runStep(background, handle, root, done);
            case ChainTask<?, T> chain -> runChain(chain, handle, root, done);
        }
    }

    private <T extends @Nullable Object> void runStep(
            BackgroundTask<T> task, RunningTask handle, boolean root, Consumer<Outcome<T>> done) {
        if (handle.isCancelled()) {
            executor.execute(() -> done.accept(hooks(task, handle, new Cancelled<>(), root)));
            return;
        }
        Thread thread = Thread.ofVirtual().name(handle.name()).unstarted(() -> work(task, handle, root, done));
        handle.stepStarting(thread);
        thread.start();
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private <T extends @Nullable Object> void work(
            BackgroundTask<T> task, RunningTask handle, boolean root, Consumer<Outcome<T>> done) {
        CountDownLatch latch = handle.currentStep();
        try {
            Outcome<T> outcome;
            try {
                outcome = new Success<>(task.run(handle, this::runHook));
            } catch (Exception | Error e) {
                outcome = new Failure<>(e, false);
            }
            Outcome<T> ended = outcome;
            executor.execute(() -> {
                if (!handle.isCancelled()) {
                    task.flushPublished();
                }
                done.accept(hooks(task, handle, ended, root));
            });
        } finally {
            handle.stepEnded(latch);
        }
    }

    private <A extends @Nullable Object, R extends @Nullable Object> void runChain(
            ChainTask<A, R> chain, RunningTask handle, boolean root, Consumer<Outcome<R>> done) {
        AbstractTask<A> first = chain.first();
        first.startInChain();
        run(first, handle, false, outcome -> {
            Consumer<Outcome<R>> finish = ended -> done.accept(hooks(chain, handle, ended, root));
            switch (outcome) {
                case Success<A> success -> runNext(chain, success.value(), handle, finish);
                case Failure<A> failure -> finish.accept(new Failure<>(failure.failure(), failure.handled()));
                case Cancelled<A> cancelled -> finish.accept(new Cancelled<>());
            }
        });
    }

    private <A extends @Nullable Object, R extends @Nullable Object> void runNext(
            ChainTask<A, R> chain, @Nullable A value, RunningTask handle, Consumer<Outcome<R>> finish) {
        if (handle.isCancelled()) {
            finish.accept(new Cancelled<>());
            return;
        }
        AtomicReference<@Nullable Task<R>> returned = new AtomicReference<>();
        Throwable thrown = Guarded.run(() -> returned.set(claim(
                Objects.requireNonNull(chain.next().apply(value), "The next function of a chain returned null"))));
        if (thrown != null) {
            finish.accept(new Failure<>(thrown, false));
        } else if (returned.get() instanceof AbstractTask<R> next) {
            run(next, handle, false, finish);
        } else {
            // There is nothing to run after the first task: Task.none().
            finish.accept(new Success<>(null));
        }
    }

    // The task that a chain's next function returned, which then belongs to the chain, unless it is Task.none().
    private static <R extends @Nullable Object> Task<R> claim(Task<R> second) {
        if (second instanceof AbstractTask<R> next) {
            next.claimForChain();
        } else if (!(second instanceof NoTask)) {
            throw new IllegalArgumentException("The next function of a chain returned a task not made by Task.of");
        }
        return second;
    }

    // Runs the hooks of a task for its outcome, its end hooks last, and returns the outcome its owner sees. A task
    // cancelled before its hooks run is cancelled, whatever its work or its steps did.
    private <T extends @Nullable Object> Outcome<T> hooks(
            AbstractTask<T> task, RunningTask handle, Outcome<T> outcome, boolean root) {
        Outcome<T> result = handle.isCancelled() ? new Cancelled<>() : outcome;
        switch (result) {
            case Success<T> success -> {
                for (Consumer<? super T> hook : task.successHooks()) {
                    runHook(() -> hook.accept(success.value()));
                }
            }
            case Failure<T> failure -> {
                List<Consumer<? super Throwable>> hooks = task.failureHooks();
                if (hooks.isEmpty()) {
                    if (root && !failure.handled()) {
                        failures.failed(failure.failure());
                    }
                } else {
                    hooks.forEach(hook -> runHook(() -> hook.accept(failure.failure())));
                    result = new Failure<>(failure.failure(), true);
                }
            }
            case Cancelled<T> cancelled -> task.cancelHooks().forEach(this::runHook);
        }
        task.endHooks().forEach(this::runHook);
        return result;
    }

    private void runHook(Runnable hook) {
        Throwable thrown = Guarded.run(hook);
        if (thrown != null) {
            failures.failed(thrown);
        }
    }

    /** How a task ended. */
    private sealed interface Outcome<T extends @Nullable Object> {}

    // The value is null for a Void result, and for a chain whose next function returns Task.none().
    private record Success<T extends @Nullable Object>(
            @Nullable T value) implements Outcome<T> {}

    private record Failure<T extends @Nullable Object>(Throwable failure, boolean handled) implements Outcome<T> {}

    private record Cancelled<T extends @Nullable Object>() implements Outcome<T> {}
}
