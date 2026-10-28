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
            case CLOSE_WINDOW ->
                builder.handler(context -> {
                    if (view.window().isMain()) {
                        registry.services().lifecycle().requestExit();
                    } else {
                        view.dialog().ifPresent(DialogControl::cancel);
                    }
                });
            case OK -> builder.handler(context -> view.dialog().ifPresent(DialogControl::ok));
            case CANCEL -> builder.handler(context -> view.dialog().ifPresent(DialogControl::cancel));
        };
        return configured.bind();
    }

    // Disabled unless a PreferencesHandler bean exists, which the injector's definitions tell.
    private CommandBuilder preferences(CommandBuilder builder) {
        boolean available = registry.injector()
                .getBeanDefinition(Key.of(PreferencesHandler.class))
                .isPresent();
        return builder.enabled(() -> Property.of(available))
                .handler(context -> registry.injector()
                        .getInstance(PreferencesHandler.class)
                        .showPreferences());
    }
}
