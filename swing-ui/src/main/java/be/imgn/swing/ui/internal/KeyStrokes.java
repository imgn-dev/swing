package be.imgn.swing.ui.internal;

import java.awt.event.InputEvent;
import java.util.regex.Pattern;
import javax.swing.KeyStroke;

/** Parses shortcuts: {@link KeyStroke#getKeyStroke(String)}'s syntax, where {@code shortcut} is the menu modifier. */
public final class KeyStrokes {

    private static final Pattern SHORTCUT = Pattern.compile("\\bshortcut\\b");

    private KeyStrokes() {}

    /**
     * Parses a shortcut.
     *
     * @param shortcut the shortcut, such as {@code shortcut S}
     * @param macOS whether the menu modifier is Meta rather than Control
     * @return the key stroke
     * @throws IllegalArgumentException if the shortcut does not parse
     */
    public static KeyStroke parse(String shortcut, boolean macOS) {
        String replaced = SHORTCUT.matcher(shortcut).replaceAll(macOS ? "meta" : "ctrl");
        KeyStroke keyStroke = KeyStroke.getKeyStroke(replaced);
        if (keyStroke == null) {
            throw new IllegalArgumentException("Not a key stroke: " + shortcut);
        }
        return keyStroke;
    }

    /**
     * Writes a key stroke as a shortcut, so that it moves between systems: its modifiers in the order {@code shortcut},
     * {@code ctrl}, {@code meta}, {@code alt}, {@code shift}, the menu modifier written {@code shortcut}, then the
     * key's name, as {@link KeyStroke#toString()} names it: {@code shortcut shift S}.
     *
     * @param keyStroke the key stroke, pressed
     * @param macOS whether the menu modifier is Meta rather than Control
     * @return the shortcut
     */
    public static String format(KeyStroke keyStroke, boolean macOS) {
        int modifiers = keyStroke.getModifiers();
        int menu = macOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;
        StringBuilder shortcut = new StringBuilder();
        if ((modifiers & menu) != 0) {
            shortcut.append("shortcut ");
        }
        if (macOS && (modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            shortcut.append("ctrl ");
        }
        if (!macOS && (modifiers & InputEvent.META_DOWN_MASK) != 0) {
            shortcut.append("meta ");
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            shortcut.append("alt ");
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            shortcut.append("shift ");
        }
        String written = keyStroke.toString();
        return shortcut.append(written.substring(written.lastIndexOf(' ') + 1)).toString();
    }
}
