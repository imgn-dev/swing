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
import java.util.function.Consumer;

/**
 * The files the user opened or saved last, most recent first, at most ten. They are saved to {@code recent-files.toml}
 * in the state directory after each change, and loaded at startup. Every method is called on the EDT.
 */
@Singleton
public final class RecentFiles {

    private static final String FILE_NAME = "recent-files.toml";
    private static final int LIMIT = 10;
    private static final System.Logger LOGGER = System.getLogger(RecentFiles.class.getName());

    private final ObservableList<Path> files = ObservableList.of();
    private final Path file;
    private final Consumer<Write> writes;
    private final Object writing = new Object();
    // The version of the list, increased at each change, on the EDT; and the last version written, under writing.
    private long version;
    private long written;

    // Written atomically in a task, which the test harness waits for as for a command's.
    @Inject
    RecentFiles(ApplicationDirectories directories, TaskRunner tasks, Scope application) {
        this(
                directories.state().resolve(FILE_NAME),
                write -> tasks.start(
                        Task.of(() -> {
                            write.run();
                            return write;
                        }),
                        application,
                        "recent files",
                        true));
    }

    /**
     * Creates the recent files of a file, whose writes the given consumer runs.
     *
     * @param file the file the list is saved to
     * @param writes runs each write, in a task in the application
     */
    RecentFiles(Path file, Consumer<Write> writes) {
        this.file = file;
        this.writes = writes;
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

    // Written from a copy taken on the EDT. The writes are ordered: one that would replace a newer list writes nothing.
    private void save() {
        long saved = ++version;
        RecentFileList list = snapshot();
        writes.accept(() -> write(saved, list));
    }

    private RecentFileList snapshot() {
        return new RecentFileList(files.get().stream().map(Path::toString).toList());
    }

    private void write(long saved, RecentFileList list) throws IOException {
        synchronized (writing) {
            if (saved <= written) {
                return;
            }
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Toml.writeAtomically(Toml.toTable(list), file);
            written = saved;
        }
    }

    /**
     * Writes the last list now, unless it is written already; the shutdown does, since it cancels the tasks still
     * running. Called on the EDT.
     */
    void flush() {
        try {
            write(version, snapshot());
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Could not save the recent files to " + file, e);
        }
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

    /** A write of the list. */
    @FunctionalInterface
    interface Write {
        /**
         * Writes the list.
         *
         * @throws IOException if the write fails
         */
        void run() throws IOException;
    }
}
