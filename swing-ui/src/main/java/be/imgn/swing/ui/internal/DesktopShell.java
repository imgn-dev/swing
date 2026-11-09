package be.imgn.swing.ui.internal;

import static java.util.Objects.requireNonNull;

import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.TaskService;
import be.imgn.swing.ui.SystemShell;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;

/** The real {@link SystemShell}, on {@code java.awt.Desktop}. */
final class DesktopShell implements SystemShell {

    private final TaskService tasks;
    private final Scope application;

    DesktopShell(TaskService tasks, Scope application) {
        this.tasks = tasks;
        this.application = application;
    }

    @Override
    public boolean canBrowse() {
        return supports(Desktop.Action.BROWSE);
    }

    @Override
    public void browse(URI uri) {
        requireNonNull(uri, "uri");
        if (!canBrowse()) {
            throw new UnsupportedOperationException("The system can't browse " + uri);
        }
        // The shutdown cancels the task, which belongs to the application scope.
        tasks.run(
                Task.of(() -> {
                    Desktop.getDesktop().browse(uri);
                    return uri;
                }),
                application);
    }

    @Override
    public boolean canOpen() {
        return supports(Desktop.Action.OPEN);
    }

    @Override
    public void open(Path file) {
        requireNonNull(file, "file");
        if (!canOpen()) {
            throw new UnsupportedOperationException("The system can't open " + file);
        }
        tasks.run(
                Task.of(() -> {
                    Desktop.getDesktop().open(file.toFile());
                    return file;
                }),
                application);
    }

    private static boolean supports(Desktop.Action action) {
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action);
    }
}
