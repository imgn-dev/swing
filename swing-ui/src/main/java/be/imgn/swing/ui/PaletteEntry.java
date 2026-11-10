package be.imgn.swing.ui;

import java.util.Objects;
import javax.swing.KeyStroke;
import org.jspecify.annotations.Nullable;

/**
 * A command the {@link CommandPalette} lists.
 *
 * @param command the command
 * @param text the command's text, without its mnemonic marker
 * @param shortcut the command's effective shortcut, the keymap's first, or {@code null} for none
 */
public record PaletteEntry(
        BoundCommand command, String text, @Nullable KeyStroke shortcut) {

    /**
     * Creates the entry.
     *
     * @param command the command
     * @param text the command's text, without its mnemonic marker
     * @param shortcut the command's effective shortcut, or {@code null} for none
     * @throws NullPointerException if {@code command} or {@code text} is {@code null}
     */
    public PaletteEntry {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(text, "text");
    }
}
