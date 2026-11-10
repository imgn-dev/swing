package be.imgn.swing.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    /**
     * Asks for a file to open. Each type is a filter, in order, the first one selected, beside the chooser's "All
     * files" filter; no type offers that filter only. The chooser starts in the directory last chosen in a chooser of
     * this window, else in the user's home directory.
     *
     * @param types the types of files
     * @return the chosen file, empty if the user cancelled
     * @throws NullPointerException if {@code types} or a type is {@code null}
     */
    Optional<Path> openFile(List<FileType> types);

    /**
     * Asks for a file to save to, starting with a suggested name. A name without one of the selected type's extensions
     * gets its first one; with the "All files" filter, the name stays as typed. A file that already exists asks whether
     * to replace it before the chooser closes. The chooser starts as {@link #openFile(List)} does.
     *
     * @param types the types of files
     * @param suggestedName the name the chooser starts with
     * @return the chosen file, empty if the user cancelled
     * @throws NullPointerException if an argument or a type is {@code null}
     */
    Optional<Path> saveFile(List<FileType> types, String suggestedName);

    /**
     * Asks whether to save the changes of a document, with Save, Don't Save and Cancel buttons. Enter is Save; Escape
     * and closing the dialog are Cancel.
     *
     * @param documentName the document's name
     * @return the button chosen
     * @throws NullPointerException if {@code documentName} is {@code null}
     */
    SaveChoice askSave(String documentName);

    /**
     * Shows the command palette over the window: an undecorated modal dialog with a field and the entries the field's
     * text matches, as {@link CommandPalette#search(List, String)} ranks them. Typing filters; Up and Down move the
     * selection; Enter chooses the selected entry; Escape, and the dialog losing the focus, cancel.
     *
     * @param entries the entries, in the order of {@link CommandPalette#entries()}
     * @return the entry chosen, or empty when the user cancels
     * @throws NullPointerException if {@code entries} is {@code null}
     */
    Optional<PaletteEntry> chooseCommand(List<PaletteEntry> entries);
}
