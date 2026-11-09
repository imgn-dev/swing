package be.imgn.swing.desktop;

import static java.util.Objects.requireNonNull;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ObservableList;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.desktop.internal.RecentFileList;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlMappingException;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.UnknownKeys;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The files the user opened or saved last, most recent first, at most ten. They are saved to {@code recent-files.toml}
 * in the state directory after each change, and loaded at startup. Every method is called on the EDT.
 */
@Singleton
public final class RecentFiles {

    /** The name of the file the recent files are saved to, in the state directory. */
    public static final String FILE_NAME = "recent-files.toml";

    private static final int LIMIT = 10;
    private static final System.Logger LOGGER = System.getLogger(RecentFiles.class.getName());

    private final ObservableList<Path> files = ObservableList.of();
    private final Path file;
    private final TaskRunner tasks;
    private final Scope application;

    @Inject
    RecentFiles(ApplicationDirectories directories, TaskRunner tasks, Scope application) {
        this.file = directories.state().resolve(FILE_NAME);
        this.tasks = tasks;
        this.application = application;
    }

    /**
     * Returns the recent files, most recent first.
     *
     * @return the files, as absolute paths
     */
    public ObservableList<Path> files() {
        return files;
    }

    /**
     * Puts a file first, removing it from further down, and keeps the ten most recent.
     *
     * @param recent the file, normalized to an absolute path
     * @throws NullPointerException if {@code recent} is {@code null}
     */
    public void add(Path recent) {
        Path normalized = requireNonNull(recent, "recent").toAbsolutePath().normalize();
        files.remove(normalized);
        files.add(0, normalized);
        while (files.size() > LIMIT) {
            files.remove(files.size() - 1);
        }
        save();
    }

    /**
     * Removes a file.
     *
     * @param recent the file, normalized to an absolute path
     * @throws NullPointerException if {@code recent} is {@code null}
     */
    public void remove(Path recent) {
        if (files.remove(requireNonNull(recent, "recent").toAbsolutePath().normalize())) {
            save();
        }
    }

    /** Removes every file. */
    public void clear() {
        files.clear();
        save();
    }

    // Written atomically in a task, from a copy taken on the EDT. The test harness waits for it as for a command's.
    private void save() {
        RecentFileList list =
                new RecentFileList(files.get().stream().map(Path::toString).toList());
        tasks.start(
                Task.of(() -> {
                    Files.createDirectories(file.getParent());
                    Toml.writeAtomically(Toml.toTable(list), file);
                    return file;
                }),
                application,
                "recent files",
                true);
    }

    /** Loads the saved files; a file that doesn't bind is discarded with a warning, and the list starts empty. */
    void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            RecentFileList list = Toml.bind(Toml.parse(file).root(), RecentFileList.class, UnknownKeys.IGNORE);
            List<Path> loaded = list.files().stream()
                    .map(path -> file.getFileSystem().getPath(path))
                    .limit(LIMIT)
                    .toList();
            files.setAll(loaded);
        } catch (IOException | TomlParseException | TomlMappingException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Discarded the recent files " + file + ": " + e.getMessage());
        }
    }
}
