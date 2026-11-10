package be.imgn.swing.docking.internal;

import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;

/** The default beans of docking: ModernDocking's surfaces, which the test harness replaces with headless ones. */
final class DockingBeans {

    private DockingBeans() {}

    @Provides
    @Default
    @Singleton
    static DockSurfaces surfaces() {
        return new ModernSurfaces();
    }
}
