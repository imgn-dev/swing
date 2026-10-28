package be.imgn.swing.ui.internal;

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
}
