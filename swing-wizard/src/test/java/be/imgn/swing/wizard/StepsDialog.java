package be.imgn.swing.wizard;

import be.imgn.inject.Inject;
import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.StandardCommands;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** A wizard of three steps: a required name, a required city, and a note. */
public final class StepsDialog implements DialogView<String> {

    @Exposed
    final JLabel nameLabel = new JLabel();

    @Exposed
    final JLabel cityLabel = new JLabel();

    @Exposed
    final JLabel noteLabel = new JLabel();

    @Exposed
    final JPanel identity;

    @Exposed
    final JPanel address;

    @Exposed
    final JPanel note;

    private final JTextField nameField = new JTextField(10);
    private final JTextField cityField = new JTextField(10);
    private final JTextField noteField = new JTextField(10);
    private final Property<String> name = Property.of("");
    private final Property<String> city = Property.of("");
    private final Property<String> text = Property.of("");
    private final Form form;
    private final Wizard wizard;

    @Inject
    StepsDialog(Form form, Wizard wizard) {
        this.form = form;
        this.wizard = wizard;
        this.identity = FormPanel.create().row(nameLabel, nameField).build();
        this.address = FormPanel.create().row(cityLabel, cityField).build();
        this.note = FormPanel.create().row(noteLabel, noteField).build();
    }

    @Override
    public JComponent content() {
        form.bind(nameField, name).check(Rule.required("A name is required"));
        form.bind(cityField, city).check(Rule.required("A city is required"));
        form.bind(noteField, text);
        return wizard.steps(identity, address, note);
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return wizard.buttons(commands);
    }

    @Override
    public Optional<String> result() {
        return Optional.of(name.get() + ", " + city.get());
    }

    JTextField nameField() {
        return nameField;
    }

    JTextField cityField() {
        return cityField;
    }

    JTextField noteField() {
        return noteField;
    }

    Wizard wizard() {
        return wizard;
    }
}
