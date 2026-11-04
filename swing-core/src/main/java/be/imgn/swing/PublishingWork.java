package be.imgn.swing;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The work of a {@link Task} that publishes partial results while it runs: {@link Task#of(PublishingWork, Consumer)}.
 *
 * @param <T> the type of the result
 * @param <P> the type of the published values
 */
@FunctionalInterface
public interface PublishingWork<T extends @Nullable Object, P> {

    /**
     * Runs the work, on the task's virtual thread.
     *
     * @param context the task's context
     * @param publish publishes a value, from any thread, while the work runs; it throws {@link NullPointerException}
     *     for {@code null}, and {@link IllegalStateException} once the work has returned
     * @return the result
     * @throws Exception if the work fails
     */
    T run(TaskContext context, Consumer<? super P> publish) throws Exception;
}
