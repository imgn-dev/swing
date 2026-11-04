package be.imgn.swing.examples.notes;

import be.imgn.swing.Messages;
import java.nio.file.Path;

/** The messages of {@link NotesApp} and its {@link PreferencesDialog}. */
@Messages
public interface NotesMessages {

    /**
     * Returns the status shown once the notes are saved.
     *
     * @param file the file written
     * @return the status
     */
    String saved(Path file);

    /**
     * Returns the requirement of the font size's format.
     *
     * @return the requirement
     */
    String wholeNumber();

    /**
     * Returns the requirement of the font size's range.
     *
     * @param min the smallest size
     * @param max the largest size
     * @return the requirement
     */
    String fontSizeRange(int min, int max);
}
