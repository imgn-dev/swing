package be.imgn.swing.docking.internal;

import be.imgn.swing.ui.Window;
import be.imgn.swing.ui.internal.AwtWindow;

/** Creates the surfaces that ModernDocking draws, the default ones. */
final class ModernSurfaces implements DockSurfaces {

    @Override
    public DockSurface create(Window window, DockSurface.Listener listener) {
        if (!(window instanceof AwtWindow real)) {
            throw new IllegalStateException("ModernDocking draws a dock in a real window only");
        }
        return new ModernSurface(real.awt(), listener);
    }
}
