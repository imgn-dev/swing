package be.imgn.swing.internal;

import be.imgn.gaia.core.time.Ticker;
import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.Exit;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.UiExecutor;
import java.lang.System.Logger.Level;
import java.time.InstantSource;

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
}
