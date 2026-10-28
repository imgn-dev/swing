package be.imgn.swing.examples.saf;

import be.imgn.swing.Messages;

/** The messages of {@link DocumentExample}. */
@Messages
public interface DocumentMessages {

    /**
     * Returns the question asked before exiting with modifications.
     *
     * @param file the file's name
     * @return the question
     */
    String confirmExit(String file);

    /**
     * Returns the description of the filter of text files.
     *
     * @return the description
     */
    String txtExtDescription();

    /**
     * Returns the message of a file that could not be saved.
     *
     * @param file the file
     * @param reason the reason
     * @return the message
     */
    String saveFailed(String file, String reason);

    /**
     * Returns the message of a file that could not be loaded.
     *
     * @param file the file
     * @param reason the reason
     * @return the message
     */
    String loadFailed(String file, String reason);

    /**
     * Returns the title of the window.
     *
     * @param file the file's name
     * @param application the application's name
     * @return the title
     */
    String title(String file, String application);
}
