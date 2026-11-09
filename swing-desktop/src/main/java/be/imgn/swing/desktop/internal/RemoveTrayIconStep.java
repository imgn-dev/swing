package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Singleton;
import be.imgn.swing.ShutdownStep;
import be.imgn.swing.desktop.Notifications;

/** Removes the notifications' icon from the system tray at shutdown. */
@Singleton
@IntoSet(ShutdownStep.class)
public final class RemoveTrayIconStep implements ShutdownStep {

    private static final int ORDER = 310;

    private final Notifications notifications;

    @Inject
    RemoveTrayIconStep(Notifications notifications) {
        this.notifications = notifications;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void run() {
        if (notifications instanceof TrayNotifications tray) {
            tray.remove();
        }
    }
}
