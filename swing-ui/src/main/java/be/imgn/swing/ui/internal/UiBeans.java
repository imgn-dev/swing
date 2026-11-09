package be.imgn.swing.ui.internal;

import be.imgn.inject.Default;
import be.imgn.inject.IntoSet;
import be.imgn.inject.Provides;
import be.imgn.inject.Singleton;
import be.imgn.swing.EventExecutor;
import be.imgn.swing.Scope;
import be.imgn.swing.TaskService;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.ui.LookAndFeelInstaller;
import be.imgn.swing.ui.OnEdt;
import be.imgn.swing.ui.SystemShell;
import be.imgn.swing.ui.TaskbarStatus;
import be.imgn.swing.ui.ValidationFeedback;
import java.lang.annotation.Annotation;
import java.util.concurrent.Executor;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The beans the UI module provides through which core reaches the UI, and its own defaults. */
final class UiBeans {

    private static final System.Logger LOGGER = System.getLogger(UiBeans.class.getName());

    private UiBeans() {}

    @Provides
    @Singleton
    static UiExecutor uiExecutor() {
        return new EdtUiExecutor();
    }

    // The bus hands @OnEdt deliveries to the UiExecutor rather than to EdtExecutor, so that the test harness's
    // executor runs them with the other posted work.
    @Provides
    @Singleton
    @IntoSet(EventExecutor.class)
    static EventExecutor onEdt(UiExecutor executor) {
        return new EventExecutor() {
            @Override
            public Class<? extends Annotation> annotation() {
                return OnEdt.class;
            }

            @Override
            public Executor executor() {
                return executor::execute;
            }
        };
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
    static ValidationFeedback borderFeedback() {
        return new BorderValidationFeedback();
    }

    @Provides
    @Default
    @Singleton
    static UiMode uiMode() {
        return UiMode.fromSystemProperties();
    }

    @Provides
    @Default
    @Singleton
    static SystemShell systemShell(TaskService tasks, Scope application) {
        return new DesktopShell(tasks, application);
    }

    @Provides
    @Default
    @Singleton
    static TaskbarStatus taskbarStatus(UiExecutor executor, WindowRegistry registry) {
        return new DefaultTaskbarStatus(
                TaskbarAdaptor.system(),
                executor,
                () -> registry.activeFrame()
                        .map(WindowHost::window)
                        .filter(AwtWindow.class::isInstance)
                        .map(window -> ((AwtWindow) window).awt()));
    }
}
