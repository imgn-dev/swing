package be.imgn.swing.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.gaia.core.platform.Platform;
import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.swing.Exit;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.UiExecutor;
import java.lang.System.Logger.Level;
import java.time.InstantSource;
import java.util.Optional;

/** Core's fallbacks for the beans through which core reaches the UI. */
final class CoreBeans {

    private static final System.Logger LOGGER = System.getLogger(CoreBeans.class.getName());

    private CoreBeans() {}

    @Provides
    @Default
    @Singleton
    static UiExecutor uiExecutor() {
        return new UiExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public boolean isUiThread() {
                return true;
            }
        };
    }

    @Provides
    @Default
    @Singleton
    static FailureHandler failureHandler() {
        return failure -> LOGGER.log(Level.ERROR, "Unhandled failure", failure);
    }

    @Provides
    @Default
    @Singleton
    static Exit exit() {
        return System::exit;
    }

    @Provides
    @Default
    @Singleton
    static Ticker ticker() {
        return Ticker.system();
    }

    @Provides
    @Default
    @Singleton
    static InstantSource instantSource() {
        return InstantSource.system();
    }

    @Provides
    @Default
    @Singleton
    static Platform platform() {
        return Platform.current();
    }

    // The directories follow the injected platform, so a platform bound in their place moves them as well.
    @Provides
    @Default
    @Singleton
    static ApplicationDirectories directories(ApplicationInfo application, Platform platform) {
        Optional<String> vendor = application.vendor();
        Optional<String> qualifier = application.qualifier();
        if (vendor.isPresent() && qualifier.isPresent()) {
            return platform.applicationDirectories(qualifier.get(), vendor.get(), application.name());
        }
        if (vendor.isPresent()) {
            return platform.applicationDirectories(vendor.get(), application.name());
        }
        return platform.applicationDirectories(application.name());
    }
}
