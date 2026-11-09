package be.imgn.swing.ui;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JToolBar;
import org.jspecify.annotations.Nullable;

/**
 * A view shown in a frame of its own, with the frame's menu bar and toolbar: the application class of a single-frame
 * application, or a view that {@link Frames} opens.
 *
 * <p>The framework sets the frame's title from the view class's {@code title} resource, else the displayed name, and
 * its icon image from the application's {@code icon}, before the view is built, so that a title the view sets in
 * {@link #content()} wins. It then applies the view's component resources, puts its content, menu bar and toolbar in
 * the frame, calls {@link #configureFrame(JFrame)}, places the frame and shows it.
 */
public interface FrameView extends View {

    /**
     * Returns the menu bar of the frame.
     *
     * @return the menu bar, or {@code null} for none
     */
    default @Nullable JMenuBar menuBar() {
        return null;
    }

    /**
     * Returns the toolbar of the frame, placed at {@code BorderLayout.NORTH}.
     *
     * @return the toolbar, or {@code null} for none
     */
    default @Nullable JToolBar toolBar() {
        return null;
    }

    /**
     * Configures the frame, before it is packed and its saved state is restored.
     *
     * @param frame the frame
     */
    default void configureFrame(JFrame frame) {}

    /**
     * Returns whether the saved bounds of the frame are restored.
     *
     * @return {@code false} for a frame that must always open where {@link #configureFrame(JFrame)} puts it
     */
    default boolean restoreBounds() {
        return true;
    }
}
