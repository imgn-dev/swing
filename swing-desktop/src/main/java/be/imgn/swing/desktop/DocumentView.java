package be.imgn.swing.desktop;

import be.imgn.swing.ui.View;
import java.nio.file.Path;

/**
 * A view that edits a file: a frame view, the application class of a single-frame application included. The framework
 * gives it a {@link Document}, the commands of {@link DocumentCommands}, the save prompt on close and exit, a title
 * that follows the document, and recent files.
 *
 * @param <T> the contents of the document
 */
public interface DocumentView<T> extends View {

    /**
     * Returns the format of the document's file, read once, when the view's commands are attached.
     *
     * @return the format
     */
    DocumentFormat<T> format();

    /**
     * Returns the contents to write, on the EDT, before a write.
     *
     * @return the contents
     */
    T toSave();

    /**
     * Puts read contents in the view's components, on the EDT, after a read. The framework then marks the view's form
     * clean and clears its undo stack.
     *
     * @param contents the contents
     */
    void loaded(T contents);

    /**
     * Called on the EDT once the document is written, such as to show that it was.
     *
     * @param file the file written
     */
    default void saved(Path file) {}
}
