package be.imgn.swing.ui.internal;

import be.imgn.inject.ExposeAs;
import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.FailureHandler;
import be.imgn.swing.ui.Dialogs;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.util.Objects;
import java.util.Optional;

/** The UI module's {@link FailureHandler}: the error dialog, owned by the focused window, else by the main window. */
@Singleton
@ExposeAs(FailureHandler.class)
public final class DialogFailureHandler implements FailureHandler {

    private static final System.Logger LOGGER = System.getLogger(DialogFailureHandler.class.getName());

    private final WindowRegistry registry;
    private final UiTexts texts;

    @Inject
    DialogFailureHandler(WindowRegistry registry, UiTexts texts) {
        this.registry = registry;
        this.texts = texts;
    }

    @Override
    public void failed(Throwable failure) {
        LOGGER.log(System.Logger.Level.ERROR, "Unhandled failure", failure);
        if (!EventQueue.isDispatchThread()) {
            EventQueue.invokeLater(() -> show(failure));
            return;
        }
        show(failure);
    }

    private void show(Throwable failure) {
        String message = Objects.requireNonNullElse(
                failure.getMessage(), failure.getClass().getName());
        Optional<WindowHost> window = focused().or(registry::main);
        if (window.isPresent()) {
            window.get().injector().getInstance(Dialogs.class).error(message, failure);
        } else {
            ErrorDialog.show(null, texts, texts.error(), message, failure);
        }
    }

    private Optional<WindowHost> focused() {
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        return registry.windows().stream()
                .filter(window ->
                        window.window() instanceof AwtWindow real && real.awt().equals(focused))
                .findFirst();
    }
}
