package be.imgn.swing.docking.internal;

import be.imgn.swing.ui.Window;

/**
 * Creates the surface of each dock: ModernDocking's by default, the headless one in the test harness, which binds its
 * own.
 */
public interface DockSurfaces {

    /**
     * Creates the surface of a frame's dock.
     *
     * @param window the frame
     * @param listener what the surface reports to
     * @return the surface
     */
    DockSurface create(Window window, DockSurface.Listener listener);
}
