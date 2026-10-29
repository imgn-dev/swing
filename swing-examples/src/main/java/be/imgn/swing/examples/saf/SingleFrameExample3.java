package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ExitVeto;
import be.imgn.swing.ui.Dialogs;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Swing;
import java.awt.BorderLayout;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * A button that exits, and an exit veto that asks for a confirmation. The button's text and icon are the exit
 * command's, which the class's resources override; its font and colors, the panel's titled border too.
 */
@Application(name = "SingleFrameExample3")
public class SingleFrameExample3 implements SingleFrame, ExitVeto {

    @Exposed
    final JPanel panel = new JPanel(new BorderLayout());

    @Exposed
    final JButton button = new JButton();

    private final StandardCommands commands;
    private final Dialogs dialogs;
    private final ExampleMessages messages;

    @Inject
    SingleFrameExample3(StandardCommands commands, Dialogs dialogs, ExampleMessages messages) {
        this.commands = commands;
        this.dialogs = dialogs;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SingleFrameExample3.class, args);
    }

    @Override
    public JComponent content() {
        button.setAction(action());
        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Returns the action of the button.
     *
     * @return the exit command's action
     */
    Action action() {
        return commands.exit().asAction();
    }

    @Override
    public boolean canExit() {
        return dialogs.confirm(messages.reallyExit());
    }
}
