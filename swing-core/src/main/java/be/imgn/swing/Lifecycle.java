package be.imgn.swing;

/**
 * Requests the shutdown of the application. A singleton.
 *
 * <p>A request may be made from any thread. It hands the shutdown to the {@link UiExecutor}: with the UI module, it
 * returns at once and the shutdown runs on the EDT; with core's default, the shutdown runs on the calling thread before
 * it returns. A request made while a shutdown runs is ignored.
 *
 * <p>The shutdown asks every {@link ExitVeto}, from the innermost scope to the outermost, and stops at the first one
 * that returns {@code false}. Otherwise it cancels the running tasks and waits for them, runs every
 * {@link ShutdownStep}, closes the root injector and calls {@link Exit#exit(int)}.
 */
public interface Lifecycle {

    /** Requests the shutdown of the application, with the exit status 0. */
    void requestExit();

    /**
     * Requests the shutdown of the application.
     *
     * @param status the exit status
     */
    void requestExit(int status);
}
