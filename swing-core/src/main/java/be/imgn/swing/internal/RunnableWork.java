package be.imgn.swing.internal;

import be.imgn.swing.TaskContext;
import be.imgn.swing.TaskWork;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The work of a task made of a {@link Runnable}, which has no result. */
public final class RunnableWork implements TaskWork<@Nullable Void> {

    private final Runnable work;

    /**
     * Creates the work.
     *
     * @param work the runnable
     */
    public RunnableWork(Runnable work) {
        this.work = Objects.requireNonNull(work, "work");
    }

    // TaskWork's result is @Nullable for Void, which NullAway does not see without its JSpecify mode.
    @Override
    @SuppressWarnings("NullAway")
    public @Nullable Void run(TaskContext context) {
        work.run();
        return null;
    }
}
