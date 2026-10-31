package be.imgn.swing.ui.internal;

import java.util.HashMap;
import java.util.Map;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/** The shortcuts of the commands attached in a window, in its root pane's {@code WHEN_IN_FOCUSED_WINDOW} input map. */
public final class ShortcutBindings {

    private static final System.Logger LOGGER = System.getLogger(ShortcutBindings.class.getName());

    private final JRootPane rootPane;
    private final UiMode mode;
    private final Map<KeyStroke, String> commands = new HashMap<>();

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
     * its shortcut once; another command with the same shortcut is reported.
     *
     * @param keyStroke the shortcut
     * @param command the command's identity: the class it belongs to and its id, as {@code p.CounterApp#increment}
     * @param action the action to run
     * @throws IllegalStateException if another command has the shortcut, in strict mode
     */
    public void register(KeyStroke keyStroke, String command, Action action) {
        String previous = commands.get(keyStroke);
        if (previous != null) {
            if (!previous.equals(command)) {
                mode.report(
                        LOGGER,
                        "The commands " + previous + " and " + command + " have the same shortcut " + keyStroke
                                + " in one window");
            }
            return;
        }
        commands.put(keyStroke, command);
        String key = "be.imgn.swing.command:" + command;
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key);
        rootPane.getActionMap().put(key, action);
    }

    /**
     * Returns the command of a shortcut.
     *
     * @param keyStroke the shortcut
     * @return the command's identity, or {@code null} if no command has it
     */
    public @Nullable String commandOf(KeyStroke keyStroke) {
        return commands.get(keyStroke);
    }
}
