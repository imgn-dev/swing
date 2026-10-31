package be.imgn.swing.testing;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.internal.RunningTask;
import be.imgn.swing.internal.Settling;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.swing.ui.internal.Edt;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/**
 * The tester's {@link UiExecutor}: the EDT, with the posted work and the tasks commands started settled before
 * assertions.
 */
final class TestExecutor implements UiExecutor, Settling {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ROUNDS = 10_000;

    private volatile @Nullable TaskRunner tasks;

    void attach(TaskRunner runner) {
        this.tasks = runner;
    }

    @Override
    public void execute(Runnable task) {
        EventQueue.invokeLater(task);
    }

    @Override
    public boolean isUiThread() {
        return EventQueue.isDispatchThread();
    }

    @Override
    public void settle() {
        if (EventQueue.isDispatchThread()) {
            return;
        }
        for (int round = 0; round < MAX_ROUNDS; round++) {
            boolean waited = awaitTasks();
            AtomicBoolean empty = new AtomicBoolean();
            Edt.invokeAndWait(() ->
                    empty.set(Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() == null));
            if (empty.get() && !waited && runningTasks() == 0) {
                return;
            }
        }
    }

    // The tasks commands started: a command's activation waits for its task, not for the other tasks.
    private List<RunningTask> commandTasks() {
        TaskRunner runner = tasks;
        return runner == null
                ? List.of()
                : runner.running().stream().filter(RunningTask::isCommand).toList();
    }

    private int runningTasks() {
        return commandTasks().size();
    }

    // Returns whether some task was running.
    private boolean awaitTasks() {
        List<RunningTask> running = commandTasks();
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
