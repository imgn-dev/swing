package be.imgn.swing;

/**
 * Runs work on the user-interface thread. Core's default runs the work at once on the calling thread; the UI module
 * provides one that runs it on the EDT.
 */
public interface UiExecutor {

    /**
     * Runs a task on the user-interface thread.
     *
     * @param task the task
     */
    void execute(Runnable task);

    /**
     * Returns whether the calling thread is the user-interface thread.
     *
     * @return {@code true} on the user-interface thread
     */
    boolean isUiThread();
}
