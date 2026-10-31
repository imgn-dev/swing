package be.imgn.swing.ui.internal;

import java.awt.Rectangle;
import java.util.Map;
import java.util.Optional;

/**
 * The saved state of a window.
 *
 * @param bounds the bounds, if saved
 * @param maximized whether the main frame was maximized
 * @param components the state of the view's components, by field name
 */
public record SavedWindow(Optional<Rectangle> bounds, boolean maximized, Map<String, SavedComponent> components) {

    /**
     * Creates the state of a window.
     *
     * @param bounds the bounds, if saved
     * @param maximized whether the main frame was maximized
     * @param components the state of the view's components, by field name; copied
     */
    public SavedWindow {
        bounds = bounds.map(Rectangle::new);
        components = Map.copyOf(components);
    }
}
