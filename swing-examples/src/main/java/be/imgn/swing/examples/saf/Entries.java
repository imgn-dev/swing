package be.imgn.swing.examples.saf;

import be.imgn.swing.Settings;
import be.imgn.toml.TomlConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The entries of {@link LocalStorageExample1}, saved in {@code entries.toml}.
 *
 * @param entries the entries, in the order they were added
 */
@Settings("entries")
@TomlConfig
public record Entries(Map<String, String> entries) {

    /** Copies the entries, keeping their order. */
    public Entries {
        entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** Creates the defaults: no entry. */
    public Entries() {
        this(Map.of());
    }
}
