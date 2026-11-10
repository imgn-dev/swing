package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.Optional;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A wizard whose second step starts with a combo box, which takes the focus as a text field does. */
public final class ComboStepDialog implements DialogView<Void> {

    @Exposed
    final JLabel nameLabel = new JLabel();

    @Exposed
    final JLabel colorLabel = new JLabel();

    @Exposed
    final JLabel shadeLabel = new JLabel();

    @Exposed
    final JPanel first;

    @Exposed
    final JPanel second;

    private final JComboBox<String> colors = new JComboBox<>(new String[] {"Red", "Blue"});
    private final Wizard wizard;

    @Inject
    ComboStepDialog(Wizard wizard) {
        this.wizard = wizard;
        this.first = FormPanel.create().row(nameLabel, new JTextField(10)).build();
        this.second = FormPanel.create()
                .row(colorLabel, colors)
                .row(shadeLabel, new JTextField(10))
                .build();
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

    JComboBox<String> colors() {
        return colors;
    }
}
