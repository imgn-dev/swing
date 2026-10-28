package be.imgn.swing;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.TaskRunner;

/** Runs tasks in a scope, without blocking any input. */
@Singleton
public final class TaskService {

    private final TaskRunner runner;

    @Inject
    TaskService(TaskRunner runner) {
        this.runner = runner;
    }

    /**
     * Runs a task in a scope, on a virtual thread named {@code task}. Closing the scope cancels the task.
     *
     * @param task the task, which must not have run yet
     * @param scope the scope the task belongs to
     * @throws IllegalStateException if the task has already run, or the scope is closed
     */
    public void run(Task<?> task, Scope scope) {
        runner.start(task, scope, "task");
    }
}
