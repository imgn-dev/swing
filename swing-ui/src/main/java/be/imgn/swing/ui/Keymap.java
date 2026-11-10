package be.imgn.swing.ui;

import be.imgn.gaia.core.platform.Platform;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.internal.KeyStrokes;
import be.imgn.swing.ui.internal.KeymapFile;
import be.imgn.swing.ui.internal.RefreshPhase;
import be.imgn.swing.ui.internal.ShortcutBindings;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowRegistry;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/**
 * The shortcuts the user assigned, removed and reset, saved to {@code keymap.toml} in the application's config
 * directory, and applied to every open window. A singleton.
 *
 * <p>The keymap knows a command by its owner's binary name and its id,
 * {@code be.imgn.swing.desktop.DocumentCommands#save}, the standard commands' owner being {@code StandardCommands}: a
 * mapping applies to that command in every view and every window. A command's effective shortcut is its mapping, none
 * for a removed one, else the shortcut of its resources. In a window, a mapped shortcut wins over another command's
 * shortcut from resources, which then has none there. Every method is called on the EDT.
 */
@Singleton
public final class Keymap {

    private static final System.Logger LOGGER = System.getLogger(Keymap.class.getName());

    private final KeymapFile file;
    private final WindowRegistry windows;
    private final boolean macOS;
    // The mappings, in the order they were made or read; a removed shortcut is a mapping to none.
    private final Map<String, KeyStroke> assigned = new LinkedHashMap<>();
    private final Set<String> removed = new LinkedHashSet<>();
    // Changes made in a batch, applied once it ends.
    private int batching;
    private boolean pending;

    @Inject
    Keymap(KeymapFile file, WindowRegistry windows, Platform platform) {
        this.file = file;
        this.windows = windows;
        this.macOS = platform.isMacOS();
    }

    /**
     * Returns the effective shortcut of a command: its mapping, else the shortcut of its resources.
     *
     * @param command the command
     * @return the shortcut, or empty for none
     * @throws NullPointerException if {@code command} is {@code null}
     */
    public Optional<KeyStroke> shortcut(BoundCommand command) {
        Objects.requireNonNull(command, "command");
        return Optional.ofNullable(effective(command.identity(), command.defaultKeyStroke()));
    }

    /**
     * Returns the shortcut of a command's resources, whatever the keymap maps it to.
     *
     * @param command the command
     * @return the shortcut, or empty for none
     * @throws NullPointerException if {@code command} is {@code null}
     */
    public Optional<KeyStroke> defaultShortcut(BoundCommand command) {
        Objects.requireNonNull(command, "command");
        return Optional.ofNullable(command.defaultKeyStroke());
    }

    /**
     * Maps a command to a shortcut, and removes the mapping of any other command mapped to the same key stroke, so that
     * two mappings never share one. It applies at once to every open window, and is saved.
     *
     * @param command the command
     * @param shortcut the shortcut
     * @throws NullPointerException if an argument is {@code null}
     */
    public void assign(BoundCommand command, KeyStroke shortcut) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(shortcut, "shortcut");
        String identity = command.identity();
        assigned.entrySet()
                .removeIf(entry ->
                        !entry.getKey().equals(identity) && entry.getValue().equals(shortcut));
        assigned.put(identity, shortcut);
        removed.remove(identity);
        changed();
    }

    /**
     * Maps a command to no shortcut. It applies at once to every open window, and is saved.
     *
     * @param command the command
     * @throws NullPointerException if {@code command} is {@code null}
     */
    public void remove(BoundCommand command) {
        Objects.requireNonNull(command, "command");
        String identity = command.identity();
        assigned.remove(identity);
        removed.add(identity);
        changed();
    }

    /**
     * Removes the mapping of a command, which takes the shortcut of its resources again. It applies at once to every
     * open window, and is saved.
     *
     * @param command the command
     * @throws NullPointerException if {@code command} is {@code null}
     */
    public void reset(BoundCommand command) {
        Objects.requireNonNull(command, "command");
        String identity = command.identity();
        assigned.remove(identity);
        removed.remove(identity);
        changed();
    }

    /** Removes every mapping. It applies at once to every open window, and is saved. */
    public void resetAll() {
        assigned.clear();
        removed.clear();
        changed();
    }

    // The effective shortcut of a command, given the shortcut of its resources.
    @Nullable
    KeyStroke effective(String identity, @Nullable KeyStroke defaults) {
        if (removed.contains(identity)) {
            return null;
        }
        KeyStroke mapped = assigned.get(identity);
        return mapped != null ? mapped : defaults;
    }

    // Where the command's effective shortcut comes from, which decides collisions in a window.
    ShortcutBindings.Priority priority(String identity, boolean framework) {
        if (assigned.containsKey(identity)) {
            return ShortcutBindings.Priority.KEYMAP;
        }
        return framework ? ShortcutBindings.Priority.STANDARD : ShortcutBindings.Priority.RESOURCES;
    }

    // Applies several changes with one save and one pass over the windows, as the keymap dialog's OK does.
    void batch(Runnable changes) {
        batching++;
        try {
            changes.run();
        } finally {
            batching--;
        }
        if (batching == 0 && pending) {
            pending = false;
            changed();
        }
    }

    private void changed() {
        if (batching > 0) {
            pending = true;
            return;
        }
        file.save(saved());
        for (WindowHost window : windows.windows()) {
            List<ViewHost> views = window.views();
            for (RefreshPhase phase : List.of(RefreshPhase.UNREGISTER, RefreshPhase.COMMANDS, RefreshPhase.REGISTER)) {
                views.forEach(view -> view.refresh(phase));
            }
        }
    }

    private Map<String, String> saved() {
        Map<String, String> shortcuts = new LinkedHashMap<>();
        assigned.forEach((identity, shortcut) -> shortcuts.put(identity, KeyStrokes.format(shortcut, macOS)));
        removed.forEach(identity -> shortcuts.put(identity, ""));
        return shortcuts;
    }

    // Reads the saved mappings, before any command is attached. A shortcut that doesn't parse is dropped, and of two
    // mappings that share one, which only a file edited by hand holds, the first in the file wins.
    void load() {
        file.load().forEach((identity, shortcut) -> {
            if (shortcut.isEmpty()) {
                removed.add(identity);
            } else {
                map(identity, shortcut);
            }
        });
    }

    private void map(String identity, String shortcut) {
        KeyStroke keyStroke;
        try {
            keyStroke = KeyStrokes.parse(shortcut, macOS);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Dropped the shortcut " + shortcut + " of " + identity + ": " + e.getMessage());
            return;
        }
        if (assigned.containsValue(keyStroke)) {
            LOGGER.log(
                    Level.WARNING,
                    "Dropped the shortcut " + shortcut + " of " + identity + ": another command of the keymap has it");
        } else {
            assigned.put(identity, keyStroke);
        }
    }
}
