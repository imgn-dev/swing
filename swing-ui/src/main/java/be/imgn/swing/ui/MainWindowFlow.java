package be.imgn.swing.ui;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.Arguments;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.internal.ExitVetoes;
import be.imgn.swing.ui.internal.ApplicationIcon;
import be.imgn.swing.ui.internal.MainWindowStarter;
import be.imgn.swing.ui.internal.SwingWindows;
import be.imgn.swing.ui.internal.TaskbarAdaptor;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Taskbar;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToolBar;
import javax.swing.WindowConstants;
import org.jspecify.annotations.Nullable;

/**
 * Starts the windows of the application: the main window of a single-frame application, or the first frames of a
 * multi-frame one.
 */
@Singleton
@ExposeAs(MainWindowStarter.class)
final class MainWindowFlow implements MainWindowStarter {

    private final ViewBuilder builder;
    private final UiServices services;
    private final FrameFlow frames;
    private final ExitVetoes vetoes;

    @Inject
    MainWindowFlow(ViewBuilder builder, UiServices services, FrameFlow frames, ExitVetoes vetoes) {
        this.builder = builder;
        this.services = services;
        this.frames = frames;
        this.vetoes = vetoes;
    }

    @Override
    public Runnable start(Class<?> type, Injector root, DisposalScope application) {
        // Before the first window, so that the Dock shows the application's icon rather than Java's.
        TaskbarAdaptor taskbar = TaskbarAdaptor.system();
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            root.getInstance(ApplicationIcon.class).image().ifPresent(taskbar::setIconImage);
        }
        Runnable ready;
        if (SingleFrame.class.isAssignableFrom(type)) {
            ready = single(type, root, application);
        } else if (MultiFrame.class.isAssignableFrom(type)) {
            ready = multi(type.asSubclass(MultiFrame.class), root, application);
        } else {
            throw new IllegalStateException(type.getName() + " must implement SingleFrame or MultiFrame");
        }
        wireDesktop();
        return ready;
    }

    private Runnable single(Class<?> type, Injector root, DisposalScope application) {
        Window window = services.windows().createFrame();
        if (window instanceof SwingWindows.FrameWindow real) {
            real.frame().setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            real.frame().addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    services.lifecycle().requestExit();
                }
            });
        }
        // Before the view is built, so that a title or icon the view sets in content() wins.
        frames.title(window, services.resources().table(type));
        frames.icon(window);
        WindowHost host =
                builder.openWindow(window, root, application, null, WindowHost.Kind.MAIN, "main", "main window");
        ViewHost view = frames.build(
                host, Optional.of(type), Optional.empty(), injector -> injector.getInstance(type), built -> {});
        SingleFrame single = (SingleFrame) view.view().orElseThrow();
        frames.place(host, single);
        services.windows().show(window);
        frames.track(host);
        return single::ready;
    }

    private Runnable multi(Class<? extends MultiFrame> type, Injector root, DisposalScope application) {
        MultiFrame multi = root.getInstance(type);
        // The application class is a singleton of the root injector, whose vetoes are otherwise contributions only.
        if (multi instanceof ExitVeto veto) {
            vetoes.register(application, veto);
        }
        multi.start(frames, root.getInstance(Arguments.class));
        if (frames.views().isEmpty()) {
            throw new IllegalStateException(type.getName() + ".start opened no frame");
        }
        return multi::ready;
    }

    static void place(JRootPane rootPane, JComponent content, @Nullable JMenuBar menuBar, @Nullable JToolBar toolBar) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(content, BorderLayout.CENTER);
        if (toolBar != null) {
            panel.add(toolBar, BorderLayout.NORTH);
        }
        rootPane.setContentPane(panel);
        if (menuBar != null) {
            rootPane.setJMenuBar(menuBar);
        }
    }

    // On macOS, the application menu's Quit, About and Preferences run the commands of the active frame's view.
    private void wireDesktop() {
        if (!services.isMacOS() || !Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> {
                // A veto that shows a dialog pumps a nested event loop, so the answer can't be posted after the
                // command: the lifecycle tells us when a veto cancels, and a shutdown that goes through exits the JVM.
                if (services.lifecycle().isShuttingDown()) {
                    response.cancelQuit();
                    return;
                }
                Runnable[] withdraw = new Runnable[1];
                withdraw[0] = services.lifecycle().onCancel(() -> {
                    withdraw[0].run();
                    response.cancelQuit();
                });
                Optional<CommandRegistry> registry = activeRegistry();
                if (registry.isEmpty()) {
                    services.lifecycle().requestExit();
                } else if (!registry.get().standard(Standard.EXIT).execute(system(SystemAction.QUIT))) {
                    withdraw[0].run();
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> activeRegistry()
                    .ifPresent(registry -> registry.standard(Standard.ABOUT).execute(system(SystemAction.ABOUT))));
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(event -> activeRegistry()
                    .ifPresent(registry ->
                            registry.standard(Standard.PREFERENCES).execute(system(SystemAction.PREFERENCES))));
        }
    }

    private Optional<CommandRegistry> activeRegistry() {
        return frames.activeHost()
                .map(host -> host.views().getFirst().injector().getInstance(CommandRegistry.class));
    }

    private static CommandContext system(SystemAction action) {
        return CommandContext.of(new Trigger.OperatingSystem(action));
    }
}
