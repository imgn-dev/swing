package be.imgn.swing.ui;

import be.imgn.inject.Key;
import be.imgn.swing.Property;
import be.imgn.swing.ui.internal.DialogControl;
import be.imgn.swing.ui.internal.FocusTracker;
import be.imgn.swing.ui.internal.ViewHost;
import javax.swing.TransferHandler;

/** Creates the standard commands of a view, with their handlers. */
final class StandardHandlers {

    private final CommandRegistry registry;

    StandardHandlers(CommandRegistry registry) {
        this.registry = registry;
    }

    BoundCommand create(Standard standard) {
        CommandBuilder builder = registry.command(StandardCommands.class, standard.id());
        FocusTracker focus = registry.services().focus();
        ViewHost view = registry.view();
        CommandBuilder configured = switch (standard) {
            case EXIT ->
                builder.handler(context -> registry.services().lifecycle().requestExit());
            case ABOUT ->
                builder.handler(
                        context -> registry.injector().getInstance(Ui.class).showDialog(AboutDialog.class));
            case PREFERENCES -> preferences(builder);
            case CUT ->
                builder.enabled(focus::canCut).handler(context -> focus.transfer(TransferHandler.getCutAction()));
            case COPY ->
                builder.enabled(focus::canCopy).handler(context -> focus.transfer(TransferHandler.getCopyAction()));
            case PASTE ->
                builder.enabled(focus::canPaste).handler(context -> focus.transfer(TransferHandler.getPasteAction()));
            case SELECT_ALL -> builder.enabled(focus::canSelectAll).handler(context -> focus.selectAll());
            case UNDO, REDO -> undoCommand(builder, standard == Standard.UNDO);
            case CLOSE_WINDOW ->
                builder.handler(context -> {
                    if (view.window().isFrame()) {
                        registry.injector().getInstance(FrameFlow.class).close(view.window());
                    } else {
                        view.dialog().ifPresent(DialogControl::cancel);
                    }
                });
            case OK, CANCEL, APPLY -> dialogCommand(builder, standard, view);
        };
        return configured.bind();
    }

    private static CommandBuilder dialogCommand(CommandBuilder builder, Standard standard, ViewHost view) {
        return switch (standard) {
            case OK -> builder.handler(context -> view.dialog().ifPresent(control -> ok(view, control)));
            case APPLY -> builder.enabled(() -> form(view).dirty()).handler(context -> apply(view));
            case CANCEL -> builder.handler(context -> view.dialog().ifPresent(DialogControl::cancel));
            default -> throw new IllegalArgumentException(standard + " is not a command of dialog views");
        };
    }

    // Acts on the view's undo stack, enabled while it can undo or redo.
    private CommandBuilder undoCommand(CommandBuilder builder, boolean undo) {
        UndoStack stack = registry.injector().getInstance(UndoStack.class);
        return undo
                ? builder.enabled(stack::canUndo).handler(context -> stack.undo())
                : builder.enabled(stack::canRedo).handler(context -> stack.redo());
    }

    // Disabled unless a PreferencesHandler bean exists, which the injector's definitions tell.
    private CommandBuilder preferences(CommandBuilder builder) {
        boolean available = registry.injector()
                .getBeanDefinition(Key.of(PreferencesHandler.class))
                .isPresent();
        return builder.enabled(() -> Property.of(available))
                .handler(context -> registry.injector()
                        .getInstance(PreferencesHandler.class)
                        .showPreferences(registry.injector().getInstance(Ui.class)));
    }

    // OK stays enabled while the form is invalid, so that activating it shows the user what is wrong.
    private static void ok(ViewHost view, DialogControl control) {
        Form form = form(view);
        if (!form.validate()) {
            return;
        }
        if (form.dirty().get()) {
            dialog(view).commit();
        }
        control.ok();
    }

    private static void apply(ViewHost view) {
        Form form = form(view);
        if (!form.validate()) {
            return;
        }
        dialog(view).commit();
        form.markClean();
    }

    // The dialog's form is the Form bean of the dialog view's scope: a dialog that binds nothing has an empty one.
    private static Form form(ViewHost view) {
        return view.injector().getInstance(Form.class);
    }

    private static DialogView<?> dialog(ViewHost view) {
        return (DialogView<?>) view.view().orElseThrow();
    }
}
