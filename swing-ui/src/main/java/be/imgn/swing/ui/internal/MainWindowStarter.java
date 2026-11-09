package be.imgn.swing.ui.internal;

import be.imgn.inject.Injector;
import be.imgn.swing.internal.DisposalScope;

/** Starts the windows of the application class; called on the EDT. */
public interface MainWindowStarter {

    /**
     * Creates and shows the main window of a single-frame application class, with the class as its view, or calls the
     * {@code start} of a multi-frame one, which opens the first frames.
     *
     * @param type the application class
     * @param root the root injector
     * @param application the application's scope
     * @return what calls the application class's {@code ready()}
     * @throws IllegalStateException if the class implements neither {@code SingleFrame} nor {@code MultiFrame}, or a
     *     multi-frame application opened no frame
     */
    Runnable start(Class<?> type, Injector root, DisposalScope application);
}
