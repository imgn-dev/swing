package be.imgn.swing.internal;

import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;

/** The hot reload's default switch, which the test harness replaces. */
final class HotReloadBeans {

    private HotReloadBeans() {}

    @Provides
    @Default
    @Singleton
    static HotReloading hotReloading() {
        return new HotReloading(Boolean.getBoolean(HotReloading.DEV_PROPERTY));
    }
}
