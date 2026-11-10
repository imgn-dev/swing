package be.imgn.swing.ui.internal;

import be.imgn.toml.TomlConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The content of {@code keymap.toml}: the shortcuts the user mapped, by command identity, {@code ""} for a removed one.
 *
 * @param shortcuts the shortcuts, in the resources' syntax
 */
@TomlConfig
public record SavedKeymap(Map<String, String> shortcuts) {

    /**
     * Creates the keymap.
     *
     * @param shortcuts the shortcuts, copied in their order
     */
    public SavedKeymap {
        shortcuts = Collections.unmodifiableMap(new LinkedHashMap<>(shortcuts));
    }
}
