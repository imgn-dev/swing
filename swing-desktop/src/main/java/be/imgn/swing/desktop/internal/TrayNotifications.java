package be.imgn.swing.desktop.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.UiExecutor;
import be.imgn.swing.desktop.Notifications;
import java.awt.AWTException;
import java.awt.Image;
import java.lang.System.Logger.Level;
import java.util.Optional;
import java.util.function.Supplier;

/** The real {@link Notifications}: messages of an icon the framework adds to the system tray at the first one. */
public final class TrayNotifications implements Notifications {

    private static final System.Logger LOGGER = System.getLogger(TrayNotifications.class.getName());

    private final TrayAdaptor tray;
    private final UiExecutor executor;
    private final Supplier<Optional<Image>> icon;
    private final String name;
    private boolean added;

    /**
     * Creates the notifications.
     *
     * @param tray the system tray
     * @param executor the executor that runs the calls on the EDT
     * @param icon supplies the image of the application's icon
     * @param name the application's displayed name, the icon's tool tip
     */
    public TrayNotifications(TrayAdaptor tray, UiExecutor executor, Supplier<Optional<Image>> icon, String name) {
        this.tray = tray;
        this.executor = executor;
        this.icon = icon;
        this.name = name;
    }

    @Override
    public boolean isSupported() {
        return tray.isSupported();
    }

    @Override
    public void notify(String caption, String text) {
        requireNonNull(caption, "caption");
        requireNonNull(text, "text");
        if (!tray.isSupported()) {
            LOGGER.log(Level.INFO, "Notification without a system tray: " + caption + ": " + text);
            return;
        }
        executor.execute(() -> show(caption, text));
    }

    private void show(String caption, String text) {
        if (!added) {
            try {
                tray.add(icon.get().orElse(null), name);
                added = true;
            } catch (AWTException e) {
                LOGGER.log(Level.WARNING, "Could not add the application's icon to the system tray", e);
                return;
            }
        }
        tray.displayMessage(caption, text);
    }

    /** Removes the icon from the system tray, on the EDT; a shutdown step does. */
    void remove() {
        if (added) {
            tray.remove();
            added = false;
        }
    }
}
