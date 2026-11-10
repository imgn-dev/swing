import be.imgn.swing.docking.internal.DockingTesterExtension;
import be.imgn.swing.ui.internal.TesterExtension;

/**
 * Docking: views docked in a frame, split and tabbed, rearranged by the user, opened and closed while the frame is
 * open, their layout saved with the frame's window state.
 */
module be.imgn.swing.docking {
    requires transitive be.imgn.swing.ui;
    requires modern_docking.api;
    requires modern_docking.multi_app;
    requires static org.jspecify;

    exports be.imgn.swing.docking;

    provides TesterExtension with
            DockingTesterExtension;
}
