package be.imgn.swing.ui.internal;

import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The saved state of a window.
 *
 * @param bounds the bounds, as x, y, width and height, if saved
 * @param maximized whether the frame was maximized; {@code null} for a dialog
 * @param components the state of the own view's components, by field name; {@code null} when none is saved
 * @param directory the absolute path of the directory last chosen in a file chooser of the window; {@code null} when
 *     none was
 * @param views the state of the components of the window's added views, by the binary name of their class; {@code null}
 *     when none is saved
 * @param dock the layout of the window's dock; {@code null} when it has none
 */
record SavedWindow(
        @Nullable List<Integer> bounds,
        @Nullable Boolean maximized,
        @Nullable Map<String, SavedComponent> components,
        @Nullable String directory,
        @Nullable Map<String, SavedView> views,
        @Nullable SavedLayout dock) {

    /** Creates the saved state of a window with no added view and no dock. */
    SavedWindow(
            @Nullable List<Integer> bounds,
            @Nullable Boolean maximized,
            @Nullable Map<String, SavedComponent> components,
            @Nullable String directory) {
        this(bounds, maximized, components, directory, null, null);
    }

    /** Creates the saved state of a window whose file choosers chose no directory. */
    SavedWindow(
            @Nullable List<Integer> bounds,
            @Nullable Boolean maximized,
            @Nullable Map<String, SavedComponent> components) {
        this(bounds, maximized, components, null);
    }
}
