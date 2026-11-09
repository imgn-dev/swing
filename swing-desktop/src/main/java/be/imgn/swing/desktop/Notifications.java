package be.imgn.swing.desktop;

/**
 * Shows system notifications. The JDK shows them only through the system tray: at the first notification, the framework
 * adds an icon with the application's icon and displayed name to the tray, which a shutdown step removes. Without a
 * system tray, a notification is logged and shows nothing.
 */
public interface Notifications {

    /**
     * Returns whether the system can show notifications: whether it has a system tray.
     *
     * @return {@code true} if supported
     */
    boolean isSupported();

    /**
     * Shows a notification, from any thread.
     *
     * @param caption the caption
     * @param text the text
     * @throws NullPointerException if an argument is {@code null}
     */
    void notify(String caption, String text);
}
