package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Injector;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * How the application is launched: by {@code Swing.launch} or by the test harness.
 *
 * @param platform the platform
 * @param locale the display locale
 * @param bindings adds bindings to the root injector's builder
 * @param systemProperties whether the macOS properties are set before AWT starts
 * @param onFailure handles a failure of the startup
 */
public record LaunchOptions(
        Platform platform,
        Locale locale,
        Consumer<Injector.Builder> bindings,
        boolean systemProperties,
        StartupFailure onFailure) {

    /** Handles a failure of the startup. */
    @FunctionalInterface
    public interface StartupFailure {

        /**
         * Handles a failure.
         *
         * @param failure the failure
         * @param injector the root injector, if it was built
         * @param lookAndFeelInstalled whether the look and feel was installed
         */
        void failed(Throwable failure, Optional<Injector> injector, boolean lookAndFeelInstalled);
    }
}
