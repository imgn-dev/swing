package be.imgn.swing.desktop;

import be.imgn.swing.Arguments;

/**
 * Handles a second launch of a single-instance application. An application that has a singleton bean of this type is
 * single-instance: launching it a second time forwards its arguments to the instance already running, which hands them
 * to this handler, and exits.
 */
public interface SecondInstanceHandler {

    /**
     * Handles a second launch, on the EDT, in the first instance, once the application is ready, in the order the
     * launches arrived.
     *
     * @param arguments the arguments of the second launch
     */
    void launched(Arguments arguments);
}
