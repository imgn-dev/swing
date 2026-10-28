package be.imgn.swing;

/**
 * Handles a failure nothing else handles, such as a task's with no {@code onFailure} handler. Core's default logs it at
 * {@code ERROR}; the UI module shows the error dialog.
 */
public interface FailureHandler {

    /**
     * Handles a failure.
     *
     * @param failure the failure
     */
    void failed(Throwable failure);
}
