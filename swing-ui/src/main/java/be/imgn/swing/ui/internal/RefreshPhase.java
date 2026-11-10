package be.imgn.swing.ui.internal;

/**
 * The phases of a refresh that the parts of a view take part in, after its component resources and its title from
 * resources; each phase runs for every view of a window before the next one.
 */
public enum RefreshPhase {
    /** A title that follows something else than the view's resources, such as a document's, is set again. */
    TITLE,
    /** The shortcuts of the window's commands are removed, so that commands may swap them. */
    UNREGISTER,
    /** The commands take their resources again. */
    COMMANDS,
    /** The shortcuts of the window's commands are registered again. */
    REGISTER,
    /** The menus built from descriptions take their texts again, and their dynamic sections are rebuilt. */
    MENUS,
    /** The column headers given as suppliers are read again, and the {@code Ui.render} updates run. */
    CODE
}
