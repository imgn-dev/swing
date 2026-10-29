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
     * Returns the default text of the editor of {@link SingleFrameExample4}.
     *
     * @return the text
     */
    String defaultText();

    /**
     * Returns the text of an image that is loading.
     *
     * @return the text
     */
    String loadingWait();

    /**
     * Returns the text of an image whose loading was cancelled.
     *
     * @return the text
     */
    String loadingCancelled();

    /**
     * Returns the text of an image that failed to load.
     *
     * @param image the image's location
     * @return the text
     */
    String loadingFailed(String image);

    /**
     * Returns the tooltip of an image.
     *
     * @param image the image's location
     * @param width the image's width
     * @param height the image's height
     * @return the text
     */
    String imageTooltip(String image, int width, int height);

    /**
     * Returns the status of a step of a task that does nothing.
     *
     * @param step the step
     * @return the text
     */
    String step(int step);

    /**
     * Returns the status of a task that succeeded.
     *
     * @return the text
     */
    String succeeded();

    /**
     * Returns the status of a task that was cancelled.
     *
     * @return the text
     */
    String cancelled();

    /**
     * Returns the line written when the selected property changes.
     *
     * @param selected the new value
     * @param action whether the toggle command's action is selected
     * @param check whether the check box is selected
     * @param radio whether the radio button is selected
     * @return the text
     */
    String selectedChanged(boolean selected, boolean action, boolean check, boolean radio);

    /**
     * Returns the text of the elapsed time.
     *
     * @param millis the elapsed time, in milliseconds
     * @return the text
     */
    String elapsed(long millis);

    /**
     * Returns the message of a saved file.
     *
     * @param file the file
     * @return the message
     */
    String savedFile(String file);

    /**
     * Returns the message of a loaded file.
     *
     * @param file the file
     * @return the message
     */
    String loadedFile(String file);

    /**
     * Returns the tooltip of the button that closes a tab of {@link Tabbed}.
     *
     * @return the text
     */
    String closeTab();
}
