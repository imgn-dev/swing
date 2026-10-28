package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.ApplicationDescriptor;
import be.imgn.swing.ReadyListener;
import be.imgn.swing.internal.Contributions;
import be.imgn.swing.internal.DefaultApplicationInfo;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.ui.LookAndFeelInstaller;
import java.awt.EventQueue;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

/** The startup of an application, from its descriptor to the ready signal. */
public final class Launch {

    private Launch() {}

    /**
     * Launches an application: reads its descriptor, builds the root injector, runs the startup steps on the calling
     * thread, then on the EDT installs the look and feel, checks the icons, creates and shows the main window, and
     * finally calls the ready listeners once the events posted meanwhile have run.
     *
     * @param type the application class
     * @param arguments the arguments
     * @param options how to launch
     * @return the root injector, or empty if the startup failed
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static Optional<Injector> start(Class<?> type, List<String> arguments, LaunchOptions options) {
        ApplicationDescriptor descriptor = Launcher.descriptor(type);
        if (options.systemProperties() && options.platform().isMacOS()) {
            System.setProperty("apple.awt.application.name", descriptor.name());
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
        @Nullable Injector injector = null;
        AtomicBoolean lookAndFeel = new AtomicBoolean();
        try {
            DefaultApplicationInfo info = new DefaultApplicationInfo(descriptor, options.platform());
            DisposalScope application = DisposalScope.root("application");
            Injector.Builder builder = Launcher.rootBuilder(info, arguments, options.locale(), application);
            options.bindings().accept(builder);
            Injector root = Launcher.build(builder, info);
            injector = root;
            Launcher.runStartupSteps(root);
            Edt.invokeAndWait(() -> {
                root.getInstance(LookAndFeelInstaller.class).install();
                lookAndFeel.set(true);
                root.getInstance(IconCache.class).check();
                root.getInstance(FocusTracker.class).start();
                root.getInstance(MainWindowStarter.class).start(type, root, application);
            });
            EventQueue.invokeLater(
                    () -> Contributions.of(root, ReadyListener.class).forEach(ReadyListener::ready));
            return Optional.of(root);
        } catch (RuntimeException | Error e) {
            options.onFailure().failed(e, Optional.ofNullable(injector), lookAndFeel.get());
            return Optional.empty();
        }
    }
}
