package be.imgn.swing.ui;

import be.imgn.inject.Injector;
import be.imgn.swing.Exit;
import be.imgn.swing.internal.Launcher;
import be.imgn.swing.ui.internal.Edt;
import be.imgn.swing.ui.internal.ErrorDialog;
import be.imgn.swing.ui.internal.Launch;
import be.imgn.swing.ui.internal.LaunchOptions;
import java.awt.EventQueue;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Launches applications. */
public final class Swing {

    private static final int FAILURE = 1;

    private Swing() {}

    /**
     * Launches an application, in these steps:
     *
     * <ol>
     *   <li>on the main thread, reads the descriptor of the application class; on macOS, sets
     *       {@code apple.awt.application.name} to its name and {@code apple.laf.useScreenMenuBar} to {@code true};
     *   <li>on the main thread, builds the root injector, which binds {@code ApplicationInfo}, {@code Arguments},
     *       {@code DisplayLocale} and the application {@code Scope};
     *   <li>on the main thread, runs every {@code StartupStep}, sorted by order then by class name;
     *   <li>on the EDT, installs the look and feel, checks the icons, creates the main window and shows it;
     *   <li>on the EDT, once the events posted while showing the window have run, calls every {@code ReadyListener}.
     * </ol>
     *
     * <p>A failure in steps 2 to 4 shows the error dialog if the look and feel is installed, prints the stack trace
     * otherwise, and exits with status 1 through {@link Exit}.
     *
     * @param type the application class, annotated with {@code @Application}
     * @param args the arguments
     * @throws IllegalArgumentException if no descriptor is registered for the class
     */
    public static void launch(Class<?> type, String[] args) {
        Launch.start(
                type,
                List.of(args),
                new LaunchOptions(
                        Launcher.platform(),
                        Locale.getDefault(Locale.Category.DISPLAY),
                        builder -> {},
                        root -> {},
                        true,
                        Swing::failed));
    }

    // The stack trace is printed when no dialog can show it.
    @SuppressWarnings("SystemOut")
    private static void failed(Throwable failure, Optional<Injector> injector, boolean lookAndFeelInstalled) {
        if (lookAndFeelInstalled) {
            Runnable show = () -> ErrorDialog.show(
                    null, "Error", String.valueOf(failure.getMessage()), failure, "Details", "Copy", "Close");
            if (EventQueue.isDispatchThread()) {
                show.run();
            } else {
                Edt.invokeAndWait(show);
            }
        } else {
            failure.printStackTrace();
        }
        injector.map(root -> root.getInstance(Exit.class)).orElse(System::exit).exit(FAILURE);
    }
}
