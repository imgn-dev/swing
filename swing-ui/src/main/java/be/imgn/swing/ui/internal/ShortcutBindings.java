package be.imgn.swing.ui.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/** The shortcuts of the commands attached in a window, in its root pane's {@code WHEN_IN_FOCUSED_WINDOW} input map. */
public final class ShortcutBindings {

    private static final System.Logger LOGGER = System.getLogger(ShortcutBindings.class.getName());
    private static final String KEY_PREFIX = "be.imgn.swing.command:";

    private final JRootPane rootPane;
    private final UiMode mode;
    private final Map<KeyStroke, Registration> registrations = new HashMap<>();

    /**
     * Creates the bindings of a window.
     *
     * @param rootPane the window's root pane
     * @param mode how strictly collisions are reported
     */
    public ShortcutBindings(JRootPane rootPane, UiMode mode) {
        this.rootPane = rootPane;
        this.mode = mode;
    }

    /**
     * Registers the shortcut of a command. The same command, attached through several views of the window, registers
     * its shortcut once. Between two commands with the same shortcut, the one of the higher priority has it, and the
     * other loses it, with no report; two of the same priority are reported, and the first keeps it.
     *
     * @param keyStroke the shortcut
     * @param command the command's identity: the class it belongs to and its id, as {@code p.CounterApp#increment}
     * @param action the action to run
     * @param priority where the shortcut comes from
     * @param lost runs when the command loses the shortcut to another one in this window
     * @throws IllegalStateException if another command of the same priority has the shortcut, in strict mode
     */
    public void register(KeyStroke keyStroke, String command, Action action, Priority priority, Runnable lost) {
        Registration previous = registrations.get(keyStroke);
        boolean inspector =
                previous != null && (priority == Priority.INSPECTOR || previous.priority == Priority.INSPECTOR);
        if (previous != null
                && (previous.command.equals(command) || inspector || priority.compareTo(previous.priority) <= 0)) {
            if (previous.command.equals(command)) {
                // The same command attached through another view of the window: its components lose it too.
                previous.lost.add(lost);
                return;
            }
            if (inspector || priority == previous.priority) {
                mode.report(
                        LOGGER,
                        "The commands " + previous.command + " and " + command + " have the same shortcut " + keyStroke
                                + " in one window");
            } else {
                lost.run();
            }
            return;
        }
        if (previous != null) {
            remove(previous.command);
            previous.lost.forEach(Runnable::run);
        }
        registrations.put(keyStroke, new Registration(command, priority, new ArrayList<>(List.of(lost))));
        String key = KEY_PREFIX + command;
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key);
        rootPane.getActionMap().put(key, action);
    }

    /**
     * Removes the shortcut of a command, if it has one, so that it can be registered again.
     *
     * @param command the identity of the command
     */
    public void remove(String command) {
        registrations.entrySet().removeIf(entry -> {
            if (!entry.getValue().command.equals(command)) {
                return false;
            }
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).remove(entry.getKey());
            return true;
        });
        rootPane.getActionMap().remove(KEY_PREFIX + command);
    }

    /**
     * Returns the command of a shortcut.
     *
     * @param keyStroke the shortcut
     * @return the command's identity, or {@code null} if no command has it
     */
    public @Nullable String commandOf(KeyStroke keyStroke) {
        Registration registration = registrations.get(keyStroke);
        return registration == null ? null : registration.command;
    }

    /** Where a shortcut comes from, the lowest priority first. */
    public enum Priority {
        /** The framework's own resources of a standard command, which yield to any other shortcut. */
        STANDARD,
        /** The resources of a command. */
        RESOURCES,
        /** The keymap, which the user set. */
        KEYMAP,
        /** The inspector overlay's, in development mode: a command with its shortcut is always reported. */
        INSPECTOR
    }

    private record Registration(String command, Priority priority, List<Runnable> lost) {}
}
