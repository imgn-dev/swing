package be.imgn.swing.examples.notes;

import be.imgn.swing.Settings;
import be.imgn.toml.TomlConfig;

/**
 * The settings of the editor, saved in {@code editor.toml}.
 *
 * @param fontSize the size of the editor's font
 * @param wordWrap whether the editor wraps its lines
 */
@Settings("editor")
@TomlConfig
public record EditorSettings(int fontSize, boolean wordWrap) {

    private static final int DEFAULT_FONT_SIZE = 14;

    /** Creates the defaults. */
    public EditorSettings() {
        this(DEFAULT_FONT_SIZE, true);
    }
}
