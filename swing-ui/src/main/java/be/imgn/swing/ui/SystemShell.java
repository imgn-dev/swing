package be.imgn.swing.ui;

import java.net.URI;
import java.nio.file.Path;

/**
 * Asks the system to show a URI in the browser, or a file in the application that the system associates with it,
 * through {@code java.awt.Desktop}. The UI module provides it as a singleton; the test harness replaces it with a fake
 * that records the calls.
 */
public interface SystemShell {

    /**
     * Returns whether the system can browse a URI.
     *
     * @return {@code true} if {@code Desktop} supports {@code BROWSE}
     */
    boolean canBrowse();

    /**
     * Shows a URI in the browser, in a task of the application scope, whose failure goes to the {@code FailureHandler}.
     *
     * @param uri the URI
     * @throws NullPointerException if {@code uri} is {@code null}
     * @throws UnsupportedOperationException if the system can't browse
     */
    void browse(URI uri);

    /**
     * Returns whether the system can open a file.
     *
     * @return {@code true} if {@code Desktop} supports {@code OPEN}
     */
    boolean canOpen();

    /**
     * Opens a file in the application that the system associates with it, in a task of the application scope, whose
     * failure goes to the {@code FailureHandler}.
     *
     * @param file the file
     * @throws NullPointerException if {@code file} is {@code null}
     * @throws UnsupportedOperationException if the system can't open files
     */
    void open(Path file);
}
