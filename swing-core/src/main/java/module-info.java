import be.imgn.swing.ApplicationDescriptor;
import be.imgn.swing.ResourceOpener;

/**
 * Lifecycle, scopes, observables, tasks, resources and messages, without Swing.
 *
 * <p>The implementation package is exported to the framework's other modules only, which are compiled after this one.
 */
@SuppressWarnings("module")
module be.imgn.swing {
    requires transitive be.imgn.gaia.core;
    requires transitive be.imgn.inject;
    requires transitive be.imgn.toml;
    requires static org.jspecify;

    exports be.imgn.swing;
    exports be.imgn.swing.support;
    exports be.imgn.swing.internal to
            be.imgn.swing.ui,
            be.imgn.swing.flatlaf,
            be.imgn.swing.testing;

    uses ApplicationDescriptor;
    uses ResourceOpener;
}
