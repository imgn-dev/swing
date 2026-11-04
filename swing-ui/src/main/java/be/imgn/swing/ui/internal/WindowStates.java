package be.imgn.swing.ui.internal;

import be.imgn.toml.TomlConfig;
import java.util.Map;

/**
 * The content of {@code window-state.toml}: the saved state of every window.
 *
 * @param windows the state of each window, by key
 */
@TomlConfig
record WindowStates(Map<String, SavedWindow> windows) {}
