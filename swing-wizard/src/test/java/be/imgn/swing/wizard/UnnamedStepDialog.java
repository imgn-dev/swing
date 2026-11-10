package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A wizard whose second step has a text field with no accessible name, which its build doesn't see. */
public final class UnnamedStepDialog implements DialogView<Void> {

    @Exposed
    final JPanel first = new JPanel();

    @Exposed
    final JPanel second = new JPanel();

    private final Wizard wizard;

    @Inject
    UnnamedStepDialog(Wizard wizard) {
        this.wizard = wizard;
        second.add(new JTextField(10));
    }

    @Override
    public JComponent content() {
        return wizard.steps(first, second);
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return wizard.buttons(commands);
    }

    @Override
    public Optional<Void> result() {
        return Optional.empty();
    }
}
