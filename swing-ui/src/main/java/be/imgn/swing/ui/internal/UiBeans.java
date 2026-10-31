package be.imgn.swing.ui.internal;

import be.imgn.inject.Default;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.ui.LookAndFeelInstaller;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The beans the UI module provides through which core reaches the UI, and its own defaults. */
final class UiBeans {

    private static final System.Logger LOGGER = System.getLogger(UiBeans.class.getName());

    private UiBeans() {}

    @Provides
    @Singleton
    static UiExecutor uiExecutor() {
        return new EdtExecutor();
    }

    @Provides
    @Default
    @Singleton
    static LookAndFeelInstaller systemLookAndFeel() {
        return () -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
                LOGGER.log(System.Logger.Level.WARNING, "Could not install the system look and feel", e);
            }
        };
    }

    @Provides
    @Default
    @Singleton
    static UiMode uiMode() {
        return UiMode.fromSystemProperties();
    }
}
