package be.imgn.swing;

/** What the work of a {@link Task} can tell and ask while it runs: {@code Task.of((TaskContext context) -> ...)}. */
public interface TaskContext {

    /**
     * Reports the progress of the work.
     *
     * @param fraction the fraction done, from 0 to 1, or -1 for an indeterminate progress
     * @throws IllegalArgumentException if the fraction is neither between 0 and 1 nor -1
     */
    void progress(double fraction);

    /**
     * Reports what the work is doing.
     *
     * @param text the message
     */
    void message(String text);

    /**
     * Returns whether the task is cancelled.
     *
     * @return {@code true} once the task is cancelled
     */
    boolean isCancelled();
}
