package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Two commands that set and clear the window's title, from a text field. */
@Application(name = "ActionExample2")
public class ActionExample2 implements SingleFrame {

    @Exposed
    final JTextField field = new JTextField();

    private final Ui ui;
    private final ActionExample2Commands commands;

    @Inject
    ActionExample2(Ui ui, ActionExample2Commands commands) {
        this.ui = ui;
        this.commands = commands;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(ActionExample2.class, args);
    }

    @Override
    public JComponent content() {
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(commands.clearTitle().createButton());
        buttons.add(commands.updateTitle().createButton());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(field, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.PAGE_END);
        return panel;
    }

    @Command
    void updateTitle() {
        ui.window().setTitle(field.getText());
    }

    @Command
    void clearTitle() {
        ui.window().setTitle("");
    }
}
