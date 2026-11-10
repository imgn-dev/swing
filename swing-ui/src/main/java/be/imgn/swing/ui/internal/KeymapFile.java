package be.imgn.swing.ui.internal;

import be.imgn.gaia.core.platform.ApplicationDirectories;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Scope;
import be.imgn.swing.Task;
import be.imgn.swing.internal.TaskRunner;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlMappingException;
import be.imgn.toml.TomlParseException;
import be.imgn.toml.TomlString;
import be.imgn.toml.TomlTable;
import be.imgn.toml.UnknownKeys;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The shortcuts the user mapped, saved to {@code keymap.toml} in the config directory, in tasks, which the test harness
 * waits for as for a command's.
 */
@Singleton
public final class KeymapFile {

    private static final String FILE_NAME = "keymap.toml";
    private static final System.Logger LOGGER = System.getLogger(KeymapFile.class.getName());

    private final Path file;
    private final Consumer<Write> writes;
    private final Object writing = new Object();
    // The version of the keymap, increased at each save, with its shortcuts; and the last version written; all under
    // writing.
    private long version;
    private @Nullable SavedKeymap saved;
    private long written;

    @Inject
    KeymapFile(ApplicationDirectories directories, TaskRunner tasks, Scope application) {
        this(
                directories.config().resolve(FILE_NAME),
                write -> tasks.start(
                        Task.of(() -> {
                            write.run();
                            return write;
                        }),
                        application,
                        "keymap",
                        true));
    }

    KeymapFile(Path file, Consumer<Write> writes) {
        this.file = file;
        this.writes = writes;
    }

    /**
     * Saves the shortcuts. The writes are ordered: one that would replace a newer keymap writes nothing.
     *
     * @param shortcuts the shortcuts, by command identity, {@code ""} for a removed one
     */
    public void save(Map<String, String> shortcuts) {
        long next;
        SavedKeymap keymap = new SavedKeymap(shortcuts);
        synchronized (writing) {
            next = ++version;
            saved = keymap;
        }
        writes.accept(() -> write(next, keymap));
    }

    private void write(long next, SavedKeymap keymap) throws IOException {
        synchronized (writing) {
            if (next <= written) {
                return;
            }
            Path directory = file.getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            Toml.writeAtomically(Toml.toTable(keymap), file);
            written = next;
        }
    }

    /** Writes the last keymap, if a task didn't, since the shutdown cancels the tasks still running. */
    public void flush() {
        long last;
        SavedKeymap keymap;
        synchronized (writing) {
            last = version;
            keymap = saved;
        }
        if (keymap == null) {
            return;
        }
        try {
            write(last, keymap);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Could not save the keymap to " + file, e);
        }
    }

    /**
     * Reads the saved shortcuts, in the order of the file.
     *
     * @return the shortcuts, by command identity; empty if none are saved, or if the file doesn't bind, which is logged
     *     as a warning
     */
    public Map<String, String> load() {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            TomlTable root = Toml.parse(file).root();
            Toml.bind(root, SavedKeymap.class, UnknownKeys.IGNORE);
            // The bound record's map has no order; the file's table keeps it, which collisions are decided by.
            Map<String, String> shortcuts = new LinkedHashMap<>();
            if (root.entries().get("shortcuts") instanceof TomlTable table) {
                table.entries().forEach((identity, value) -> {
                    if (value instanceof TomlString shortcut) {
                        shortcuts.put(identity, shortcut.value());
                    }
                });
            }
            return shortcuts;
        } catch (IOException | TomlParseException | TomlMappingException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Discarded the keymap " + file + ": " + e.getMessage());
            return Map.of();
        }
    }

    @FunctionalInterface
    interface Write {
        void run() throws IOException;
    }
}
