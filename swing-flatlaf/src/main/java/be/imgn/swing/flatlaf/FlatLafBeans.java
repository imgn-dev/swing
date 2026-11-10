package be.imgn.swing.flatlaf;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.Appearance;
import be.imgn.swing.Appearances;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Resources;
import be.imgn.swing.Scope;
import be.imgn.swing.StartupStep;
import be.imgn.swing.internal.Refresher;
import be.imgn.swing.internal.ResourceState;
import be.imgn.swing.internal.SwitchableAppearances;
import be.imgn.swing.ui.IconLoader;
import be.imgn.swing.ui.LookAndFeelInstaller;
import be.imgn.swing.ui.ValidationFeedback;
import be.imgn.swing.ui.internal.PeriodicActions;
import be.imgn.swing.ui.internal.SwingLocale;
import java.awt.EventQueue;

/**
 * The beans of {@code swing-flatlaf}: its look and feel installer and its validation feedback, which replace the
 * defaults, and the SVG loader.
 */
final class FlatLafBeans {

    private static final int APPEARANCE_ORDER = 90;

    private FlatLafBeans() {}

    @Provides
    @Singleton
    static FlatLafInstaller installer(ApplicationInfo application, Platform platform, Resources resources) {
        return new FlatLafInstaller(application, platform, resources, DarkMode.system(), System::getProperty);
    }

    // Installs FlatLaf in the appearance of the Appearances bean, which the test harness replaces with its own.
    @Provides
    @Singleton
    static LookAndFeelInstaller flatLaf(FlatLafInstaller installer, Appearances appearances) {
        return () -> installer.install(appearances.current().get());
    }

    @Provides
    @Singleton
    static Appearances appearances(
            FlatLafInstaller installer,
            ResourceState state,
            Refresher refresher,
            PeriodicActions periodic,
            SwingLocale swing,
            Resources resources,
            Scope application) {
        return new FlatLafAppearances(
                installer,
                new SwitchableAppearances(Appearance.LIGHT, state, refresher),
                EventQueue::invokeLater,
                read -> Thread.ofVirtual().name("appearance").start(read),
                periodic::add,
                () -> swing.apply(resources.locale()),
                application);
    }

    // Before the resources' step, so that they are read once, in the right variant. A replaced Appearances, such as the
    // test harness's, follows nothing.
    @Provides
    @Singleton
    @IntoSet(StartupStep.class)
    static StartupStep followSystem(Appearances appearances) {
        return new StartupStep() {
            @Override
            public int order() {
                return APPEARANCE_ORDER;
            }

            @Override
            public void run() {
                if (appearances instanceof FlatLafAppearances followed) {
                    followed.start();
                }
            }
        };
    }

    @Provides
    @Singleton
    static ValidationFeedback outlineFeedback() {
        return new OutlineValidationFeedback();
    }

    @Provides
    @Singleton
    @IntoSet(IconLoader.class)
    static IconLoader svgIcons() {
        return new SvgIconLoader();
    }
}
