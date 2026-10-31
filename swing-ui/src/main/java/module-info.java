import be.imgn.swing.ResourceOpener;
import be.imgn.swing.ui.internal.InternalResources;
import be.imgn.swing.ui.internal.UiResources;
import be.imgn.swing.ui.spi.ComponentInjector;
import be.imgn.swing.ui.spi.ComponentState;

/**
 * Views, windows, commands, dialogs and window state, in Swing.
 *
 * <p>The implementation package is exported to the framework's other modules only, which are compiled after this one.
 */
@SuppressWarnings("module")
module be.imgn.swing.ui {
    requires transitive be.imgn.inject;
    requires transitive be.imgn.swing;
    requires transitive java.desktop;
    requires static org.jspecify;

    exports be.imgn.swing.ui;
    exports be.imgn.swing.ui.spi;
    exports be.imgn.swing.ui.internal to
            be.imgn.swing.flatlaf,
            be.imgn.swing.testing;

    uses ComponentInjector;
    uses ComponentState;

    provides ResourceOpener with
            InternalResources,
            UiResources;
}
