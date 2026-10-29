package be.imgn.swing.ui;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.internal.DisposalScope;
import be.imgn.swing.ui.internal.MainWindowStarter;
import be.imgn.swing.ui.internal.SwingWindows;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.toml.TomlString;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JToolBar;
import javax.swing.WindowConstants;
import org.jspecify.annotations.Nullable;

/** Creates and shows the main window of a single-frame application. */
@Singleton
@ExposeAs(MainWindowStarter.class)
final class MainWindowFlow implements MainWindowStarter {

    private final ViewBuilder builder;
    private final UiServices services;

    @Inject
    MainWindowFlow(ViewBuilder builder, UiServices services) {
        this.builder = builder;
        this.services = services;
    }

    @Override
    public void start(Class<?> type, Injector root, DisposalScope application) {
        if (!SingleFrame.class.isAssignableFrom(type)) {
            throw new IllegalStateException(type.getName() + " must implement SingleFrame in milestone 1");
        }
        Window window = services.windows().createFrame();
        @Nullable JFrame frame = window instanceof SwingWindows.FrameWindow real ? real.frame() : null;
        if (frame != null) {
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent event) {
                    services.lifecycle().requestExit();
                }
            });
        }
        WindowHost host = builder.openWindow(window, root, application, null, "main", "main window");
        ViewHost view =
                builder.buildView(host, Optional.of(type), injector -> injector.getInstance(type), null, built -> {
                    SingleFrame single = (SingleFrame) built.view().orElseThrow();
                    place(window.rootPane(), single.content(), single.menuBar(), single.toolBar());
                });
        SingleFrame single = (SingleFrame) view.view().orElseThrow();
        window.setTitle(
                services.resources().table(type).entries().get("title") instanceof TomlString title
                        ? title.value()
                        : services.application().displayedName());
        if (frame != null) {
            // A fresh frame sits at (0, 0), so a sentinel location tells a configureFrame that moves it there apart
            // from one that leaves it alone; one that asks the platform for the location has moved it as well.
            Point before = new Point(-1, -1);
            frame.setLocation(before);
            single.configureFrame(frame);
            boolean moved = frame.isLocationByPlatform() || !before.equals(frame.getLocation());
            frame.pack();
            boolean restored =
                    host.injector().getInstance(WindowStateSupport.class).restore(single.restoreBounds());
            if (!moved && !restored) {
                frame.setLocationRelativeTo(null);
            }
        }
        wireDesktop(view);
        services.windows().show(window);
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

    // On macOS, the application menu's Quit, About and Preferences run the main view's commands.
    private void wireDesktop(ViewHost view) {
        if (!services.isMacOS() || !Desktop.isDesktopSupported()) {
            return;
        }
        CommandRegistry registry = view.injector().getInstance(CommandRegistry.class);
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
                if (!registry.standard(Standard.EXIT).execute(system(SystemAction.QUIT))) {
                    withdraw[0].run();
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> registry.standard(Standard.ABOUT).execute(system(SystemAction.ABOUT)));
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(
                    event -> registry.standard(Standard.PREFERENCES).execute(system(SystemAction.PREFERENCES)));
        }
    }

    private static CommandContext system(SystemAction action) {
        return CommandContext.of(new Trigger.OperatingSystem(action));
    }
}
