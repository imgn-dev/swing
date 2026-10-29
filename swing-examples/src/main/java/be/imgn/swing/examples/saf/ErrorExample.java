package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import javax.swing.Action;

/** The button of {@link SingleFrameExample3}, which here shows the error dialog, with its details. */
@Application(name = "ErrorExample")
public final class ErrorExample extends SingleFrameExample3 {

    private final Ui ui;
    private final ExampleMessages messages;
    private final ErrorExampleCommands commands;

    @Inject
    ErrorExample(Ui ui, StandardCommands standard, ExampleMessages messages, ErrorExampleCommands commands) {
        super(ui, standard, messages);
        this.ui = ui;
        this.messages = messages;
        this.commands = commands;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ErrorExample.class, args);
    }

    @Override
    Action action() {
        return commands.onButton().asAction();
    }

    @Command
    void onButton() {
        ui.dialogs().error(messages.errorOnButton(), new IllegalStateException("Wrong path"));
    }
}
