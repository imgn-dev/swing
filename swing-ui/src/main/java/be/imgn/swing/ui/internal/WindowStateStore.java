package be.imgn.swing.ui.internal;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ApplicationInfo;
import be.imgn.toml.Toml;
import be.imgn.toml.TomlParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The saved state of every window, in {@code window-state.toml} of the application's state directory. */
@Singleton
public final class WindowStateStore {

    /** The name of the file. */
    public static final String FILE_NAME = "window-state.toml";

    private static final System.Logger LOGGER = System.getLogger(WindowStateStore.class.getName());

    private final Path file;
    private final Map<String, SavedWindow> windows = new LinkedHashMap<>();

    @Inject
    WindowStateStore(ApplicationInfo application) {
        this.file = application.directories().state().resolve(FILE_NAME);
    }

    /**
     * Returns the file the state is saved to.
     *
     * @return the file
     */
    public Path file() {
        return file;
    }

    /** Reads the file. A file that does not bind is discarded with a warning, and the defaults are used. */
    public synchronized void load() {
        windows.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            windows.putAll(WindowStateFile.read(Toml.parse(file).root()));
        } catch (IOException | TomlParseException | IllegalArgumentException | ArithmeticException e) {
            windows.clear();
            LOGGER.log(System.Logger.Level.WARNING, "Discarded the window state " + file + ": " + e.getMessage());
        }
    }

    /**
     * Returns the saved state of a window.
     *
     * @param key the window's key
     * @return its state, if saved
     */
    public synchronized Optional<SavedWindow> get(String key) {
        return Optional.ofNullable(windows.get(key));
    }

    /**
     * Saves the state of a window: records it, and writes the file atomically.
     *
     * @param key the window's key
     * @param window its state
     */
    public synchronized void save(String key, SavedWindow window) {
        windows.put(key, window);
        try {
            Files.createDirectories(file.getParent());
            Toml.writeAtomically(WindowStateFile.write(windows), file);
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.ERROR, "Could not save the window state to " + file, e);
        }
    }
}
