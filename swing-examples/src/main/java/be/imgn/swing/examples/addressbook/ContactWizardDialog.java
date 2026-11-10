package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.wizard.Wizard;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The dialog that creates or modifies a contact in three steps: Guts' {@code ContactDetailWizardController}, whose
 * wizard comes from Guts' {@code WizardModule}, and here from swing-wizard's {@link Wizard}. Each step's
 * {@code step-title} resource titles it; its {@code ok()} finishes the wizard from any step.
 */
public final class ContactWizardDialog implements DialogView<Contact> {

    @Exposed
    final JLabel firstNameLabel = new JLabel();

    @Exposed
    final JLabel lastNameLabel = new JLabel();

    @Exposed
    final JLabel birthLabel = new JLabel();

    @Exposed
    final JPanel identity;

    @Exposed
    final AddressPanel home;

    @Exposed
    final AddressPanel office;

    private final ContactForm fields;
    private final Form form;
    private final AddressBookMessages messages;
    private final Wizard wizard;

    @Inject
    ContactWizardDialog(@Assisted Contact contact, Form form, AddressBookMessages messages, Wizard wizard) {
        this.fields = new ContactForm(contact);
        this.form = form;
        this.messages = messages;
        this.wizard = wizard;
        this.identity = fields.identity(FormPanel.create(), firstNameLabel, lastNameLabel, birthLabel)
                .build();
        this.home = fields.home();
        this.office = fields.office();
    }

    @Override
    public JComponent content() {
        fields.bind(form, messages);
        return wizard.steps(identity, home, office);
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return wizard.buttons(commands);
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(fields.contact());
    }
}
