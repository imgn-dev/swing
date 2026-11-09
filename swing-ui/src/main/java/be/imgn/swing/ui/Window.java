package be.imgn.swing.ui;

import java.awt.Component;
import java.awt.Image;
import javax.swing.JRootPane;

/** A window, as the framework sees it: a frame or a dialog, or a bare root pane in the test harness. */
public interface Window {

    /**
     * Returns the root pane of the window.
     *
     * @return the root pane
     */
    JRootPane rootPane();

    /**
     * Sets the title of the window.
     *
     * @param title the title
     */
    void setTitle(String title);

    /**
     * Sets the icon image of the window, which the platform shows in its title bar, task bar or window list.
     *
     * @param image the image
     * @throws NullPointerException if {@code image} is {@code null}
     */
    void setIconImage(Image image);

    /**
     * Moves the focus to a component of the window, with {@link Component#requestFocusInWindow()}. The test harness's
     * window records the component instead, since it is never realized.
     *
     * @param component the component
     * @throws NullPointerException if {@code component} is {@code null}
     */
    void focus(Component component);

    /**
     * Brings the window to the front, deiconified, and asks for the focus. The test harness's window records the
     * request instead, since it is never realized.
     */
    void toFront();
}
