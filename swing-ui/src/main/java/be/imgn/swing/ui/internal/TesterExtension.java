package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;

/**
 * What a module of the framework replaces in the test harness, such as the single instance's lock, which a test never
 * takes. The harness finds the modules on its path through the service loader, never through reflection, and touches no
 * type of a module it doesn't find.
 */
public interface TesterExtension {

    /**
     * Returns the name of the module that provides the extension, which tells the harness which of its fakes apply.
     *
     * @return the module's name, such as {@code be.imgn.swing.desktop}
     */
    String module();

    /**
     * Binds the module's replacements in the tester's root injector.
     *
     * @param builder the builder of the root injector
     */
    void bind(Injector.Builder builder);
}
