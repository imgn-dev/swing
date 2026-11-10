package be.imgn.swing.docking.internal;

import be.imgn.swing.ui.Window;

/** Creates headless surfaces, which the test harness binds in place of ModernDocking's. */
public final class HeadlessSurfaces implements DockSurfaces {

    /** Creates the factory. */
    public HeadlessSurfaces() {}

    @Override
    public DockSurface create(Window window, DockSurface.Listener listener) {
        return new HeadlessSurface(listener);
    }
}
