package be.imgn.swing;

/**
 * Told when the application is ready: once the main window is shown, and the events posted while showing it have run.
 * Contributed with {@code @IntoSet(ReadyListener.class)}.
 */
public interface ReadyListener {

    /** Called once, on the EDT, when the application is ready. */
    void ready();
}
