package be.imgn.swing;

import org.jspecify.annotations.Nullable;

/**
 * The work of a {@link Task} that uses its {@link TaskContext}.
 *
 * @param <T> the type of the result
 */
@FunctionalInterface
public interface TaskWork<T extends @Nullable Object> {

    /**
     * Runs the work, on the task's virtual thread.
     *
     * @param context the task's context
     * @return the result
     * @throws Exception if the work fails
     */
    T run(TaskContext context) throws Exception;
}
