package be.imgn.swing.ui;

/** The answer to the save prompt, {@link Dialogs#askSave(String)}. */
public enum SaveChoice {
    /**
     * Saves the changes: the close or the exit stops while the document is written, then is requested again once the
     * write succeeds.
     */
    SAVE,
    /** Discards the changes, and goes on. */
    DISCARD,
    /** Goes back to the document. */
    CANCEL
}
