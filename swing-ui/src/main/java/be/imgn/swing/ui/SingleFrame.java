package be.imgn.swing.ui;

/**
 * An application class that is the only view of the main window. Its commands and resources work as in any view.
 *
 * <p>When the application class implements it, the framework creates the main window and shows the application class in
 * it, as {@link FrameView} describes. Closing the main window requests exit. Once the application is ready, it calls
 * {@link #ready()}.
 */
public interface SingleFrame extends FrameView {

    /**
     * Called once, on the EDT, when the application is ready: the main window is shown and the events posted meanwhile
     * have run. It is called before the {@code ReadyListener}s. Work that must not delay showing the window, such as
     * loading its first data, starts here.
     */
    default void ready() {}
}
