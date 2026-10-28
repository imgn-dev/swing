package be.imgn.swing.examples.saf;

import be.imgn.swing.Messages;

/** The messages the examples show in dialogs. */
@Messages
public interface ExampleMessages {

    /**
     * Returns the question asked before exiting.
     *
     * @return the question
     */
    String reallyExit();

    /**
     * Returns the message of an error on a button.
     *
     * @return the message
     */
    String errorOnButton();

    /**
     * Returns the message of a file that cannot be opened.
     *
     * @param file the file
     * @return the message
     */
    String cannotOpen(String file);

    /**
     * Returns the message of the files listed so far.
     *
     * @param directory the directory being listed
     * @return the message
     */
    String listing(String directory);

    /**
     * Returns the message of a listing that is done.
     *
     * @param count the number of files listed
     * @return the message
     */
    String listed(int count);

    /**
     * Returns the text of the elapsed time.
     *
     * @param millis the elapsed time, in milliseconds
     * @return the text
     */
    String elapsed(long millis);
}
