package be.imgn.swing;

/**
 * A step of the shutdown, contributed with {@code @IntoSet(ShutdownStep.class)}. Steps run once the exit vetoes allowed
 * the shutdown and the running tasks ended, sorted by {@link #order()} then by class name, before the root injector is
 * closed.
 *
 * <p>The UI contributes two steps: saving window state (order 100), then disposing the windows and closing their child
 * injectors (order 200).
 */
public interface ShutdownStep {

    /**
     * Returns the rank of this step; lower runs first.
     *
     * @return the order
     */
    int order();

    /** Runs this step. */
    void run();
}
