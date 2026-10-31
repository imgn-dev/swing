package be.imgn.swing.flatlaf;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.ui.IconLoader;
import be.imgn.swing.ui.LookAndFeelInstaller;

/** The beans of {@code swing-flatlaf}: its look and feel installer, which replaces the default, and the SVG loader. */
final class FlatLafBeans {

    private FlatLafBeans() {}

    @Provides
    @Singleton
    static LookAndFeelInstaller flatLaf(ApplicationInfo application, Platform platform, Resources resources) {
        return new FlatLafInstaller(application, platform, resources, DarkMode.system(), System::getProperty);
    }

    @Provides
    @Singleton
    @IntoSet(IconLoader.class)
    static IconLoader svgIcons() {
        return new SvgIconLoader();
    }
}
