package be.imgn.swing.examples.saf;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * A toggle command, with a selected state, shared by a check box and a radio button, and a button that flips the state
 * from code: every component follows it, and the text area logs each change.
 */
@Application(name = "SelectedPropertyExample", vendor = "Sun", version = "1.0")
public final class SelectedPropertyExample implements SingleFrame {

    // The toggle: activating it flips the property, which the check box and the radio button follow.
    @Command(id = "toggleAction")
    final Property<Boolean> selected = Property.of(false);

    private final JCheckBox check = new JCheckBox();
    private final JRadioButton radio = new JRadioButton();
    private final JButton button = new JButton();
    private final JTextArea textArea = new JTextArea();
    private final SelectedPropertyExampleCommands commands;
    private final Ui ui;
    private final ExampleMessages messages;

    @Inject
    SelectedPropertyExample(SelectedPropertyExampleCommands commands, Ui ui, ExampleMessages messages) {
        this.commands = commands;
        this.ui = ui;
        this.messages = messages;
    }

    /**
     * Launches the example.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(SelectedPropertyExample.class, args);
    }

    @Override
    public JComponent content() {
        Action toggle = commands.toggleAction().asAction();
        check.setAction(toggle);
        radio.setAction(toggle);
        button.setAction(commands.buttonAction().asAction());
        JPanel buttons = new JPanel(new FlowLayout());
        buttons.add(radio);
        buttons.add(check);
        buttons.add(button);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(buttons, BorderLayout.PAGE_START);
        panel.add(new JScrollPane(textArea), BorderLayout.CENTER);
        // The command's action and the buttons follow the property before any binding of the view: the log sees them.
        ui.bind(value -> log(), selected);
        return panel;
    }

    private void log() {
        textArea.append(messages.selectedChanged(
                        selected.get(),
                        Boolean.TRUE.equals(commands.toggleAction().asAction().getValue(Action.SELECTED_KEY)),
                        check.isSelected(),
                        radio.isSelected())
                + "\n");
    }

    @Command
    void buttonAction() {
        selected.set(!selected.get());
    }
}
