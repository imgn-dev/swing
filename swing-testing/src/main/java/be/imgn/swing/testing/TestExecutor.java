package be.imgn.swing.testing;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.DefaultSettingsStore;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.internal.Settling;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.PeriodicActions;
import java.awt.EventQueue;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * The tester's {@link UiExecutor}: the EDT, with the posted work, the tasks commands started, the
 * {@code @OnVirtualThread} deliveries and the pending settings saves settled before assertions. It starts no timer for
 * the periodic actions: it runs those that are due while it settles.
 */
final class TestExecutor implements UiExecutor, Settling {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ROUNDS = 10_000;

    private volatile @Nullable TaskRunner tasks;
    private volatile @Nullable DefaultSettingsStore settings;
    private volatile @Nullable PeriodicActions periodic;

    void attach(TaskRunner runner) {
        this.tasks = runner;
    }

    void attachSettings(DefaultSettingsStore store) {
        this.settings = store;
    }

    void poll(PeriodicActions actions) {
        this.periodic = actions;
    }

    @Override
    public void execute(Runnable task) {
        EventQueue.invokeLater(task);
    }

    @Override
    public boolean isUiThread() {
        return EventQueue.isDispatchThread();
    }

    // Waits for the tasks of commands and for the @OnVirtualThread deliveries, not for the other tasks an application
    // starts.
    @Override
    public void settle() {
        settleAwaiting(task -> task.isCommand() || task.isDelivery());
    }

    /**
     * Settles as {@link #settle()} does, from the EDT, such as in a dialog's answer: another thread settles, while a
     * secondary loop on the EDT runs what it posts and what was posted before.
     *
     * @throws AssertionError if a task or a save doesn't end in time
     */
    void settleOnEdt() {
        if (!EventQueue.isDispatchThread()) {
            settle();
            return;
        }
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        // The exit is posted, so that the loop exits only once it runs, even if the settling ends before the loop is
        // entered.
        CompletableFuture<Void> settled = CompletableFuture.runAsync(
                        this::settle,
                        task -> Thread.ofPlatform().name("tester settle").start(task))
                .whenComplete((ignored, failure) -> EventQueue.invokeLater(loop::exit));
        loop.enter();
        try {
            settled.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof AssertionError failure) {
                throw failure;
            }
            throw e;
        }
    }

    /**
     * Settles as {@link #settle()} does, without waiting for the tasks of commands: an activation doesn't wait for the
     * task it starts.
     */
    void settleExceptCommands() {
        settleAwaiting(RunningTask::isDelivery);
    }

    private void settleAwaiting(Predicate<RunningTask> awaited) {
        if (EventQueue.isDispatchThread()) {
            return;
        }
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean waited = awaitTasks(awaited);
            waited |= awaitSaves();
            if (pollIsIdle() && !waited && awaitedTasks(awaited).isEmpty()) {
                return;
            }
        }
    }

    // Runs the periodic actions that are due, on the EDT after the work posted so far, and returns whether nothing is
    // posted afterwards.
    private boolean pollIsIdle() {
        AtomicBoolean empty = new AtomicBoolean();
        Edt.invokeAndWait(() -> {
            PeriodicActions actions = periodic;
            if (actions != null) {
                actions.runDue();
            }
            empty.set(Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() == null);
        });
        return empty.get();
    }

    private List<RunningTask> awaitedTasks(Predicate<RunningTask> awaited) {
        TaskRunner runner = tasks;
        return runner == null
                ? List.of()
                : runner.running().stream().filter(awaited).toList();
    }

    // Returns whether some save was pending: saves are not tasks, and wherever they come from, the tester waits for
    // them.
    private boolean awaitSaves() {
        DefaultSettingsStore store = settings;
        if (store == null || !store.hasPendingSaves()) {
            return false;
        }
        try {
            List<String> unsaved = store.awaitSaves(TIMEOUT);
            if (!unsaved.isEmpty()) {
                throw new AssertionError(
                        "The settings " + String.join(", ", unsaved) + " were not saved within " + TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the settings to be saved", e);
        }
        return true;
    }

    // Returns whether some task was running.
    private boolean awaitTasks(Predicate<RunningTask> awaited) {
        List<RunningTask> running = awaitedTasks(awaited);
        if (running.isEmpty()) {
            return false;
        }
        for (RunningTask task : running) {
            try {
                if (!task.await(TIMEOUT)) {
                    throw new AssertionError("The task " + task.name() + " did not end within " + TIMEOUT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the task " + task.name(), e);
            }
        }
        return true;
    }
}
