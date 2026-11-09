package be.imgn.swing.ui;

/** The answer to the save prompt, {@link Dialogs#askSave(String)}. */
public enum SaveChoice {
    /** Saves the changes, then goes on. */
    SAVE,
    /** Discards the changes, and goes on. */
    DISCARD,
    /** Goes back to the document. */
    CANCEL
}
