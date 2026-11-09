import be.imgn.swing.ResourceOpener;
import be.imgn.swing.desktop.internal.DesktopInternalResources;
import be.imgn.swing.desktop.internal.DesktopResources;
import be.imgn.swing.desktop.internal.DesktopTesterExtension;
import be.imgn.swing.ui.internal.TesterExtension;

/** Single instance, open-file events, notifications, documents and recent files. */
module be.imgn.swing.desktop {
    requires transitive be.imgn.swing.ui;
    requires static org.jspecify;

    exports be.imgn.swing.desktop;

    provides ResourceOpener with
            DesktopInternalResources,
            DesktopResources;
    provides TesterExtension with
            DesktopTesterExtension;
}
