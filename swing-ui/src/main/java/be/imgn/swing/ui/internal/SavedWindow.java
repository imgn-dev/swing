package be.imgn.swing.ui.internal;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The saved state of a window.
 *
 * @param bounds the bounds, as x, y, width and height, if saved
 * @param maximized whether the main frame was maximized; {@code null} for a dialog
 * @param components the state of the view's components, by field name; {@code null} when none is saved
 */
record SavedWindow(
        @Nullable List<Integer> bounds,
        @Nullable Boolean maximized,
        @Nullable Map<String, SavedComponent> components) {}
