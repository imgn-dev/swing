package be.imgn.swing.ui;

import be.imgn.inject.Inject;
import be.imgn.inject.Injector;
import be.imgn.inject.Singleton;
import be.imgn.swing.ui.internal.AwtWindow;
import be.imgn.swing.ui.internal.DialogControl;
import be.imgn.swing.ui.internal.UiServices;
import be.imgn.swing.ui.internal.ViewHost;
import be.imgn.swing.ui.internal.WindowHost;
import be.imgn.swing.ui.internal.WindowStateSupport;
import be.imgn.toml.TomlString;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/** Shows dialog views: in a modal dialog owned by the opener's window, with their own child injectors and scopes. */
@Singleton
final class DialogFlow {

    private static final String OK = "be.imgn.swing.dialog.ok";
    private static final String CANCEL = "be.imgn.swing.dialog.cancel";

    private final ViewBuilder builder;
    private final UiServices services;

    @Inject
    DialogFlow(ViewBuilder builder, UiServices services) {
        this.builder = builder;
        this.services = services;
    }

    <R> Optional<R> show(
            ViewHost opener,
            Optional<? extends Class<?>> type,
            Optional<? extends Enum<?>> variant,
            Function<Injector, ? extends DialogView<R>> resolve) {
        Window window = services.windows().createDialog(opener.window().window());
        WindowHost host = builder.openWindow(
                window,
                opener.injector(),
                opener.scope(),
                opener,
                type.map(Class::getName).orElse(""),
                "dialog");
        try {
            DialogControl control = new DialogControl(() -> close(host));
            ViewHost view = builder.buildView(host, type, variant, resolve, control, DialogFlow::place);
            // Each variant of a dialog keeps its own window state.
            host.stateKey(view.type().getName()
                    + variant.map(constant -> "."
                                    + constant.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                            .orElse(""));
            @SuppressWarnings("unchecked")
            DialogView<R> dialog = (DialogView<R>) view.view().orElseThrow();
            window.setTitle(
                    builder.table(view.type(), variant).entries().get("title") instanceof TomlString title
                            ? title.value()
                            : services.application().displayedName());
            if (window instanceof AwtWindow real && real.awt() instanceof JDialog jdialog) {
                jdialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                jdialog.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        control.cancel();
                    }
                });
                jdialog.pack();
                if (!host.injector().getInstance(WindowStateSupport.class).restore(true)) {
                    jdialog.setLocationRelativeTo(
                            opener.window().window() instanceof AwtWindow owner ? owner.awt() : null);
                }
            }
            services.windows().show(window);
            if (!control.isClosed()) {
                control.cancel();
            }
            return control.isConfirmed() ? dialog.result() : Optional.empty();
        } finally {
            builder.close(host);
        }
    }

    private void close(WindowHost host) {
        host.injector().getInstance(WindowStateSupport.class).save();
        services.windows().dispose(host.window());
    }

    private static void place(ViewHost view) {
        DialogView<?> dialog = (DialogView<?>) view.view().orElseThrow();
        StandardCommands commands = view.injector().getInstance(StandardCommands.class);
        JRootPane rootPane = view.window().window().rootPane();
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        List<BoundCommand> commandsOfButtons = dialog.buttons(commands);
        commandsOfButtons.forEach(command -> {
            JButton button = command.createButton();
            buttons.add(button);
            // The OK button is the default button, for its visual and for Enter in a component that consumes it.
            if (command.equals(commands.ok())) {
                rootPane.setDefaultButton(button);
            }
        });
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(dialog.content(), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        rootPane.setContentPane(panel);
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), OK, commands.ok());
        bind(rootPane, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL, commands.cancel());
    }

    private static void bind(JRootPane rootPane, KeyStroke keyStroke, String key, BoundCommand command) {
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, key);
        rootPane.getActionMap().put(key, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isEnabled() {
                return command.asAction().isEnabled();
            }

            @Override
            public void actionPerformed(ActionEvent event) {
                new ShortcutAction(command, keyStroke).actionPerformed(event);
            }
        });
    }
}
