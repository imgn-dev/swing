package be.imgn.swing.ui;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JToolBar;
import org.jspecify.annotations.Nullable;

/**
 * An application class that is the only view of the main window. Its commands and resources work as in any view.
 *
 * <p>When the application class implements it, the framework creates the main window, resolves the application class
 * through the view's child injector, applies its component resources, then puts its content, menu bar and toolbar in
 * the window, sets the title from the {@code title} resource, else the displayed name, calls
 * {@link #configureFrame(JFrame)}, and restores the saved window state.
 */
public interface SingleFrame extends View {

    /**
     * Returns the menu bar of the main window.
     *
     * @return the menu bar, or {@code null} for none
     */
    default @Nullable JMenuBar menuBar() {
        return null;
    }

    /**
     * Returns the toolbar of the main window, placed at {@code BorderLayout.NORTH}.
     *
     * @return the toolbar, or {@code null} for none
     */
    default @Nullable JToolBar toolBar() {
        return null;
    }

    /**
     * Configures the main frame, before it is packed and its saved state is restored.
     *
     * @param frame the frame
     */
    default void configureFrame(JFrame frame) {}

    /**
     * Returns whether the saved bounds of the main frame are restored.
     *
     * @return {@code false} for a frame that must always open where {@link #configureFrame(JFrame)} puts it
     */
    default boolean restoreBounds() {
        return true;
    }
}
