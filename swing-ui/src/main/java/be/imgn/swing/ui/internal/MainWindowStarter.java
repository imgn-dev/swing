package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.internal.DisposalScope;

/** Creates and shows the main window of the application class; called on the EDT. */
public interface MainWindowStarter {

    /**
     * Creates the main window, builds the application class as its view, and shows it.
     *
     * @param type the application class
     * @param root the root injector
     * @param application the application's scope
     */
    void start(Class<?> type, Injector root, DisposalScope application);
}
