package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Swing;
import javax.swing.Action;

/** The button of {@link SingleFrameExample3}, which here shows the error dialog, with its details. */
@Application(name = "ErrorExample")
public final class ErrorExample extends SingleFrameExample3 {

    private final Dialogs dialogs;
    private final ExampleMessages messages;
    private final ErrorExampleCommands commands;

    @Inject
    ErrorExample(StandardCommands standard, Dialogs dialogs, ExampleMessages messages, ErrorExampleCommands commands) {
        super(standard, dialogs, messages);
        this.dialogs = dialogs;
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
        dialogs.error(messages.errorOnButton(), new IllegalStateException("Wrong path"));
    }
}
