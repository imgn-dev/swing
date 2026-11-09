package be.imgn.swing.desktop;

import java.nio.file.Path;
import java.util.List;

/**
 * Opens the files the operating system hands to the application, such as those the user opens from the macOS Finder or
 * drops on the Dock icon. An application that has a singleton bean of this type receives them; on the other systems,
 * files come as arguments.
 */
public interface OpenFilesHandler {

    /**
     * Opens files, on the EDT, once the application is ready, in the order the events came.
     *
     * @param files the files, as absolute paths
     */
    void open(List<Path> files);
}
