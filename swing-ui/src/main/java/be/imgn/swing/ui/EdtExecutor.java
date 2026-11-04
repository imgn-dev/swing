package be.imgn.swing.ui;

import java.awt.EventQueue;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The executor of {@link OnEdt}: runs each task with {@link EventQueue#invokeLater(Runnable)}. A bus that the framework
 * didn't build, such as one made with {@code EventBus.create()}, uses it; the application's bus doesn't.
 */
public final class EdtExecutor implements Executor {

    /** Creates the executor; the code gaia-processor generates for {@link OnEdt} methods does. */
    public EdtExecutor() {}

    /**
     * Runs a task on the EDT, after the events already queued.
     *
     * @param task the task
     * @throws NullPointerException if {@code task} is {@code null}
     */
    @Override
    public void execute(Runnable task) {
        EventQueue.invokeLater(Objects.requireNonNull(task, "task"));
    }
}
