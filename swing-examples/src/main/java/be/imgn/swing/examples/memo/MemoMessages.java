package be.imgn.swing.examples.memo;

import be.imgn.swing.Messages;

/** The messages of {@link MemoView}, as in the vision. */
@Messages
public interface MemoMessages {

    /**
     * Returns the status shown once a note is saved, until the next change of its text.
     *
     * @param file the name of the file written
     * @return the status
     */
    String saved(String file);

    /**
     * Returns the status that counts the words of a note.
     *
     * @param count the number of words
     * @return the status
     */
    String words(int count);

    /**
     * Returns the description of the notes' files, which the file choosers show.
     *
     * @return the description
     */
    String textFiles();
}
