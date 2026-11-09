package be.imgn.swing.desktop.internal;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import org.jspecify.annotations.Nullable;

/** The calls the framework makes on {@link SystemTray}, which tests replace with a fake. */
public interface TrayAdaptor {

    /**
     * Returns whether the system has a tray.
     *
     * @return {@code true} if supported
     */
    boolean isSupported();

    /**
     * Adds an icon to the tray.
     *
     * @param image the icon's image, which the JDK requires
     * @param tooltip the icon's tool tip
     * @throws AWTException if the tray refuses it
     */
    void add(Image image, String tooltip) throws AWTException;

    /**
     * Shows a notification from the icon added.
     *
     * @param caption the caption
     * @param text the text
     */
    void displayMessage(String caption, String text);

    /** Removes the icon added, if any. */
    void remove();

    /**
     * Returns the adaptor of the system's tray.
     *
     * @return the adaptor
     */
    static TrayAdaptor system() {
        return new TrayAdaptor() {
            private @Nullable TrayIcon icon;

            @Override
            public boolean isSupported() {
                return SystemTray.isSupported();
            }

            @Override
            public void add(Image image, String tooltip) throws AWTException {
                TrayIcon added = new TrayIcon(image, tooltip);
                added.setImageAutoSize(true);
                SystemTray.getSystemTray().add(added);
                icon = added;
            }

            @Override
            public void displayMessage(String caption, String text) {
                TrayIcon added = icon;
                if (added != null) {
                    added.displayMessage(caption, text, TrayIcon.MessageType.INFO);
                }
            }

            @Override
            public void remove() {
                TrayIcon added = icon;
                if (added != null) {
                    SystemTray.getSystemTray().remove(added);
                    icon = null;
                }
            }
        };
    }
}
