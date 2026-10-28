package be.imgn.swing.ui;

import java.util.List;
import java.util.Optional;

/**
 * A view shown in a modal dialog, owned by the window of the view that opened it, with its own content, commands and
 * scope. Enter activates {@code ok()}, Escape activates {@code cancel()}; closing the dialog is {@code cancel()}.
 *
 * @param <R> the type of the result
 */
public interface DialogView<R> extends View {

    /**
     * Returns the result, read after {@code ok()}.
     *
     * @return the result
     */
    Optional<R> result();

    /**
     * Returns the commands of the dialog's buttons.
     *
     * @param commands the dialog view's own standard commands
     * @return the commands, {@code ok()} and {@code cancel()} by default
     */
    default List<BoundCommand> buttons(StandardCommands commands) {
        return List.of(commands.ok(), commands.cancel());
    }
}
