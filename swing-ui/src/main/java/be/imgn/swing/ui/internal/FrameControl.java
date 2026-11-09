package be.imgn.swing.ui.internal;

/** What the framework's other modules do to frames beyond {@code Frames}. Used on the EDT. */
public interface FrameControl {

    /**
     * Closes a frame without asking its vetoes: saves its state, disposes of it and closes its scopes.
     *
     * @param frame the frame
     */
    void discard(WindowHost frame);

    /**
     * Returns whether the application is multi-frame: it has no main window.
     *
     * @return {@code true} for a multi-frame application
     */
    boolean isMultiFrame();
}
