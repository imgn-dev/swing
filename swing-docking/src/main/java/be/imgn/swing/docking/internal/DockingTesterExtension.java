package be.imgn.swing.docking.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.ui.internal.TesterExtension;

/**
 * Replaces ModernDocking with the headless surface in the test harness, which never realizes a window, as ModernDocking
 * needs. Found through the {@code ServiceLoader}.
 */
public final class DockingTesterExtension implements TesterExtension {

    /** Creates the extension, as the {@code ServiceLoader} does. */
    public DockingTesterExtension() {}

    @Override
    public String module() {
        return "be.imgn.swing.docking";
    }

    @Override
    public void bind(Injector.Builder builder) {
        builder.bind(DockSurfaces.class, new HeadlessSurfaces());
    }
}
