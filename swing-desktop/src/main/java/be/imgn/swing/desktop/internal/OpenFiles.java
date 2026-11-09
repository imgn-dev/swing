package be.imgn.swing.desktop.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Key;
import be.imgn.inject.Singleton;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.desktop.OpenFilesHandler;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hands the files the operating system opens with the application to its {@link OpenFilesHandler} bean, if it has one
 * and {@code Desktop} supports {@code APP_OPEN_FILE}: on the EDT, once the application is ready, in the order the
 * events came. The handler is set by the last startup step, before the first window is shown.
 */
@Singleton
public final class OpenFiles {

    /** The order of the startup step that sets the handler: the last one. */
    static final int ORDER = 1000;

    private final Injector root;
    private final UiExecutor executor;
    // Guarded by this: the events received before the application is ready.
    private final List<List<Path>> pending = new ArrayList<>();
    private boolean ready;

    @Inject
    OpenFiles(Injector root, UiExecutor executor) {
        this.root = root;
        this.executor = executor;
    }

    /** Sets the open-file handler of {@code Desktop}; the startup step of order 1000 does. */
    void start() {
        if (root.getBeanDefinition(Key.of(OpenFilesHandler.class)).isEmpty()
                || !Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
            return;
        }
        Desktop.getDesktop()
                .setOpenFileHandler(event -> received(event.getFiles().stream()
                        .map(File::toPath)
                        .map(Path::toAbsolutePath)
                        .toList()));
    }

    private void received(List<Path> files) {
        synchronized (this) {
            if (!ready) {
                pending.add(files);
                return;
            }
        }
        executor.execute(() -> deliver(files));
    }

    /** Hands the events received so far to the handler, and the next ones as they come; on the EDT. */
    void ready() {
        List<List<Path>> events;
        synchronized (this) {
            ready = true;
            events = List.copyOf(pending);
            pending.clear();
        }
        events.forEach(this::deliver);
    }

    private void deliver(List<Path> files) {
        root.getInstance(OpenFilesHandler.class).open(files);
    }
}
