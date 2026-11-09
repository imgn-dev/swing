package be.imgn.swing.ui;

import be.imgn.inject.Inject;

/**
 * The standard commands of a view, with working handlers, and the superclass of every generated
 * {@code <Class>Commands}. Injectable on its own by views that declare no command. Every instance in a view returns the
 * same {@link BoundCommand} for a standard command, or the command that the view class overrides it with.
 *
 * <p>Their text and shortcuts come from the framework's resources, per platform and locale; an application overrides
 * them in its own TOML ({@code [commands.exit]}).
 */
@ViewScoped
public class StandardCommands {

    private final CommandRegistry registry;

    /**
     * Creates the standard commands of a view.
     *
     * @param registry the view's command registry
     */
    @Inject
    @SuppressWarnings("this-escape")
    public StandardCommands(CommandRegistry registry) {
        this.registry = registry;
        registry.constructing(this);
    }

    /**
     * Returns the command that asks the exit vetoes, then shuts down: {@code Lifecycle.requestExit()}.
     *
     * @return the command
     */
    public BoundCommand exit() {
        return registry.standard(Standard.EXIT);
    }

    /**
     * Returns the command that shows the {@link AboutDialog}: from top to bottom, each one that is present of the icon,
     * the displayed name, version, vendor, description, copyright and website.
     *
     * @return the command
     */
    public BoundCommand about() {
        return registry.standard(Standard.ABOUT);
    }

    /**
     * Returns the command that calls {@link PreferencesHandler#showPreferences(Ui)} with the {@link Ui} of this view;
     * disabled unless a {@link PreferencesHandler} bean exists.
     *
     * @return the command
     */
    public BoundCommand preferences() {
        return registry.standard(Standard.PREFERENCES);
    }

    /**
     * Returns the command that cuts in the focused component; enabled only when it supports it.
     *
     * @return the command
     */
    public BoundCommand cut() {
        return registry.standard(Standard.CUT);
    }

    /**
     * Returns the command that copies in the focused component; enabled only when it supports it.
     *
     * @return the command
     */
    public BoundCommand copy() {
        return registry.standard(Standard.COPY);
    }

    /**
     * Returns the command that pastes in the focused component; enabled only when it supports it.
     *
     * @return the command
     */
    public BoundCommand paste() {
        return registry.standard(Standard.PASTE);
    }

    /**
     * Returns the command that selects all in the focused component; enabled only when it supports it.
     *
     * @return the command
     */
    public BoundCommand selectAll() {
        return registry.standard(Standard.SELECT_ALL);
    }

    /**
     * Returns the command that undoes the next edit of the view's {@link UndoStack}; enabled while it can undo.
     *
     * @return the command
     */
    public BoundCommand undo() {
        return registry.standard(Standard.UNDO);
    }

    /**
     * Returns the command that redoes the next edit of the view's {@link UndoStack}; enabled while it can redo.
     *
     * @return the command
     */
    public BoundCommand redo() {
        return registry.standard(Standard.REDO);
    }

    /**
     * Returns the command that closes the current window after its vetoes; on the main frame of a single-frame
     * application, the same as {@link #exit()}.
     *
     * @return the command
     */
    public BoundCommand closeWindow() {
        return registry.standard(Standard.CLOSE_WINDOW);
    }

    /**
     * Returns the command that closes the dialog with a result, always enabled: if the dialog's form doesn't validate,
     * the dialog stays open, showing the errors; otherwise, if the form is dirty, it calls {@link DialogView#commit()},
     * then closes the dialog with {@link DialogView#result()}. The dialog's form is the {@link Form} bean of the dialog
     * view's scope.
     *
     * @return the command
     * @throws IllegalStateException outside a dialog view's scope
     */
    public BoundCommand ok() {
        return dialogCommand(Standard.OK);
    }

    /**
     * Returns the command that closes the dialog without a result, always enabled, whatever the state of its form.
     *
     * @return the command
     * @throws IllegalStateException outside a dialog view's scope
     */
    public BoundCommand cancel() {
        return dialogCommand(Standard.CANCEL);
    }

    /**
     * Returns the command that applies the dialog's form, enabled while the form is dirty: if the form validates, it
     * calls {@link DialogView#commit()}, then {@link Form#markClean()}; the dialog stays open. The dialog's form is the
     * {@link Form} bean of the dialog view's scope.
     *
     * @return the command
     * @throws IllegalStateException outside a dialog view's scope
     */
    public BoundCommand apply() {
        return dialogCommand(Standard.APPLY);
    }

    private BoundCommand dialogCommand(Standard standard) {
        if (registry.view().dialog().isEmpty()) {
            throw new IllegalStateException("The command " + standard.id() + " exists only in a dialog view, not in "
                    + registry.view().type());
        }
        return registry.standard(standard);
    }

    CommandRegistry registry() {
        return registry;
    }
}
