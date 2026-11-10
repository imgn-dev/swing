package be.imgn.swing.flatlaf;

import be.imgn.swing.Appearance;
import be.imgn.swing.Appearances;
import be.imgn.swing.Observable;
import be.imgn.swing.Scope;
import be.imgn.swing.Subscription;
import be.imgn.swing.internal.SwitchableAppearances;
import com.formdev.flatlaf.FlatLaf;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * The appearance that the theme selects: fixed with the themes {@code light} and {@code dark}; with {@code system}, the
 * system's setting, read at startup, then every 10 seconds and when the application is activated, so that a user who
 * changes it in the system's settings sees it on coming back. Each read runs the system's command off the EDT, one at a
 * time; a result that differs installs FlatLaf in the new appearance and refreshes the open windows.
 */
final class FlatLafAppearances implements Appearances {

    static final Duration PERIOD = Duration.ofSeconds(10);

    private final FlatLafInstaller installer;
    private final SwitchableAppearances appearances;
    private final Executor ui;
    private final Executor reads;
    private final BiFunction<Duration, Runnable, Subscription> schedule;
    private final Runnable installed;
    private final Scope application;
    private final AtomicBoolean reading = new AtomicBoolean();

    /**
     * Creates the appearance.
     *
     * @param installer the installer, which knows the theme and reads the system's setting
     * @param appearances switches the resources, refreshes the windows and holds the appearance
     * @param ui runs on the EDT
     * @param reads runs a read of the system's setting off the EDT
     * @param schedule runs an action periodically, on the EDT, until its subscription closes
     * @param installed runs once FlatLaf is installed again, for what its new defaults lost
     * @param application the application's scope, which ends the following
     */
    FlatLafAppearances(
            FlatLafInstaller installer,
            SwitchableAppearances appearances,
            Executor ui,
            Executor reads,
            BiFunction<Duration, Runnable, Subscription> schedule,
            Runnable installed,
            Scope application) {
        this.installer = installer;
        this.appearances = appearances;
        this.ui = ui;
        this.reads = reads;
        this.schedule = schedule;
        this.installed = installed;
        this.application = application;
    }

    @Override
    public Observable<Appearance> current() {
        return appearances.current();
    }

    /** Reads the starting appearance, off the EDT, before the resources are read; then follows the system. */
    void start() {
        appearances.start(read());
        if (installer.theme() == Theme.SYSTEM) {
            ui.execute(this::follow);
        }
    }

    private Appearance read() {
        return installer.isDark() ? Appearance.DARK : Appearance.LIGHT;
    }

    // On the EDT: the periodic reads and the activation of the application, until the application's scope closes.
    private void follow() {
        application.onClose(schedule.apply(PERIOD, this::readAgain));
        // A window activated from no window of the application: the active window also goes through no window when the
        // focus moves between two windows of the application, but then the opposite window is the other one.
        AWTEventListener activation = event -> {
            if (event.getID() == WindowEvent.WINDOW_ACTIVATED && ((WindowEvent) event).getOppositeWindow() == null) {
                readAgain();
            }
        };
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        toolkit.addAWTEventListener(activation, AWTEvent.WINDOW_EVENT_MASK);
        application.onClose(() -> toolkit.removeAWTEventListener(activation));
    }

    // A read asked for while one runs is dropped: its result would be the same.
    void readAgain() {
        if (!reading.compareAndSet(false, true)) {
            return;
        }
        reads.execute(() -> {
            try {
                Appearance appearance = read();
                ui.execute(() -> change(appearance));
            } finally {
                reading.set(false);
            }
        });
    }

    // A read that ends once the application closed changes nothing: its windows are gone.
    private void change(Appearance appearance) {
        if (application.isClosed()) {
            return;
        }
        appearances.change(appearance, () -> {
            installer.install(appearance);
            FlatLaf.updateUI();
            installed.run();
        });
    }
}
