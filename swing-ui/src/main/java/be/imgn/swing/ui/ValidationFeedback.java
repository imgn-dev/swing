package be.imgn.swing.ui;

import javax.swing.JComponent;

/**
 * Shows the error of a {@link Form}'s binding on its component, on the EDT. For a component in the viewport of a
 * {@link javax.swing.JScrollPane}, such as a text area, the form shows it on the scroll pane, which draws the
 * component's border.
 *
 * <p>{@code swing-ui} provides a default, which wraps the component's border in a compound border with a red one-pixel
 * line outside it, and sets the component's tool tip text and accessible description to the message.
 * {@code swing-flatlaf} provides one that replaces it, which sets FlatLaf's outline of the component to its error style
 * instead of changing its border.
 */
public interface ValidationFeedback {

    /**
     * Shows an error on a component. Showing another error on a component that shows one replaces its message.
     *
     * @param component the component
     * @param error the message of the error
     */
    void show(JComponent component, String error);

    /**
     * Hides the error a component shows, restoring what showing it changed. Does nothing when it shows none.
     *
     * @param component the component
     */
    void hide(JComponent component);
}
