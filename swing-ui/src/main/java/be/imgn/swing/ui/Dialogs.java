package be.imgn.swing.ui;

/** Shows standard dialogs, owned by its window. */
@WindowScoped
public interface Dialogs {

    /**
     * Asks for a confirmation.
     *
     * @param message the question
     * @return {@code true} if the user confirmed
     */
    boolean confirm(String message);

    /**
     * Shows a message.
     *
     * @param message the message
     */
    void inform(String message);

    /**
     * Shows an error, with a details area holding the failure and a Copy button.
     *
     * @param message the message
     * @param cause the failure
     */
    void error(String message, Throwable cause);
}
