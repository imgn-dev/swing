package be.imgn.swing.desktop.internal;

import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.desktop.Notifications;
import be.imgn.swing.ui.internal.ApplicationIcon;

/** The desktop's default beans, which the test harness replaces. */
final class DesktopBeans {

    private DesktopBeans() {}

    @Provides
    @Default
    @Singleton
    static Notifications notifications(UiExecutor executor, ApplicationIcon icon, ApplicationInfo application) {
        return new TrayNotifications(TrayAdaptor.system(), executor, icon::image, application.displayedName());
    }
}
