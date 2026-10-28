package be.imgn.swing.ui;

/** Creates the windows. The real one wraps a {@code JFrame} or a modal {@code JDialog}. */
public interface Windows {

    /**
     * Creates a frame.
     *
     * @return the frame
     */
    Window createFrame();

    /**
     * Creates a modal dialog.
     *
     * @param owner the window that owns the dialog
     * @return the dialog
     */
    Window createDialog(Window owner);

    /**
     * Shows a window. For a modal dialog, blocks until it closes.
     *
     * @param window the window
     */
    void show(Window window);

    /**
     * Disposes of a window.
     *
     * @param window the window
     */
    void dispose(Window window);
}
