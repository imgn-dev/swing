package be.imgn.swing.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Exit;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.Lifecycle;
import be.imgn.swing.Scope;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.UiExecutor;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** The lifecycle: the shutdown, run on the {@link UiExecutor}. */
@Singleton
public final class DefaultLifecycle implements Lifecycle {

    /** How long the shutdown waits for the cancelled tasks to end. */
    public static final Duration TASK_TIMEOUT = Duration.ofSeconds(2);

    private static final System.Logger LOGGER = System.getLogger(DefaultLifecycle.class.getName());

    private final UiExecutor executor;
    private final ExitVetoes vetoes;
    private final TaskRunner tasks;
    private final Injector injector;
    private final Scope scope;
    private final Exit exit;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final List<Runnable> cancelListeners = new CopyOnWriteArrayList<>();

    @Inject
    DefaultLifecycle(
            UiExecutor executor, ExitVetoes vetoes, TaskRunner tasks, Injector injector, Scope scope, Exit exit) {
        this.executor = executor;
        this.vetoes = vetoes;
        this.tasks = tasks;
        this.injector = injector;
        this.scope = scope;
        this.exit = exit;
    }

    @Override
    public void requestExit() {
        requestExit(0);
    }

    @Override
    public void requestExit(int status) {
        if (state.get() == State.RUNNING) {
            executor.execute(() -> shutdown(status));
        }
    }

    /**
     * Returns whether a shutdown runs or has run.
     *
     * @return {@code true} once a shutdown that no veto cancelled has started
     */
    public boolean isShuttingDown() {
        return state.get() != State.RUNNING;
    }

    /**
     * Adds a listener told when an exit veto cancels a shutdown.
     *
     * @param listener the listener, called on the {@link UiExecutor}'s thread
     * @return withdraws the listener
     */
    public Runnable onCancel(Runnable listener) {
        cancelListeners.add(listener);
        return () -> cancelListeners.remove(listener);
    }

    private void shutdown(int status) {
        if (!state.compareAndSet(State.RUNNING, State.ASKING)) {
            return;
        }
        for (ExitVeto veto : vetoes.ordered(Contributions.of(injector, ExitVeto.class))) {
            if (!veto.canExit()) {
                state.set(State.RUNNING);
                cancelListeners.forEach(Runnable::run);
                return;
            }
        }
        state.set(State.SHUTTING_DOWN);
        tasks.cancelAndAwait(TASK_TIMEOUT);
        for (ShutdownStep step : Contributions.sorted(injector, ShutdownStep.class, ShutdownStep::order)) {
            run(step);
        }
        close();
        exit.exit(status);
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private static void run(ShutdownStep step) {
        try {
            step.run();
        } catch (RuntimeException e) {
            LOGGER.log(Level.ERROR, "The shutdown step " + step.getClass().getName() + " failed", e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void close() {
        try {
            injector.close();
        } catch (RuntimeException e) {
            LOGGER.log(Level.ERROR, "Could not close the root injector", e);
        }
        if (scope instanceof DisposalScope disposal) {
            disposal.close();
        }
    }

    private enum State {
        RUNNING,
        ASKING,
        SHUTTING_DOWN
    }
}
