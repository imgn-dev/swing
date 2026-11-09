package be.imgn.swing.ui;

import be.imgn.inject.Singleton;
import be.imgn.swing.Arguments;

/**
 * An application class that opens frames from code, each showing a {@link FrameView} of its own, such as a document per
 * frame. The application class is a singleton of the root injector, not a view: it has no window of its own.
 *
 * <p>At startup, once the look and feel is installed, the framework calls {@link #start(Frames, Arguments)} on the EDT,
 * which opens the first frames. Closing the last frame requests exit. Once the application is ready, it calls
 * {@link #ready()}.
 */
@Singleton
public interface MultiFrame {

    /**
     * Opens the first frames, on the EDT. At least one frame is open when it returns.
     *
     * @param frames the frames
     * @param arguments the application's arguments
     */
    void start(Frames frames, Arguments arguments);

    /**
     * Called once, on the EDT, when the application is ready: the first frames are shown and the events posted
     * meanwhile have run. It is called before the {@code ReadyListener}s.
     */
    default void ready() {}
}
