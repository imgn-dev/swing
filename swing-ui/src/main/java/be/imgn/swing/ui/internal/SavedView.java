package be.imgn.swing.ui.internal;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The saved state of an added view of a window.
 *
 * @param components the state of its components, by field name; {@code null} when none is saved
 */
record SavedView(@Nullable Map<String, SavedComponent> components) {}
