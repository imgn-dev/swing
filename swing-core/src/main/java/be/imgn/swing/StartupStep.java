package be.imgn.swing;

/**
 * A step of the startup, contributed with {@code @IntoSet(StartupStep.class)}. Steps run on the main thread, off the
 * EDT, after the root injector is built, sorted by {@link #order()} then by class name. They may do I/O.
 *
 * <p>The framework contributes steps for resources (order 100) and window state loading (order 200).
 */
public interface StartupStep {

    /**
     * Returns the rank of this step; lower runs first.
     *
     * @return the order
     */
    int order();

    /** Runs this step. */
    void run();
}
