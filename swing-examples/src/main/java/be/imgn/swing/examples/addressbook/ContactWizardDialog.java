package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The dialog that creates or modifies a contact in three steps, with previous and next buttons: Guts'
 * {@code ContactDetailWizardController}, whose wizard comes from Guts' {@code WizardModule}, and here from the dialog's
 * own commands. Its {@code ok()} finishes the wizard from any step.
 */
public final class ContactWizardDialog implements DialogView<Contact> {

    private static final int NAME_COLUMNS = 20;
    private static final int GAP = 8;

    @Exposed
    final JLabel firstNameLabel = new JLabel();

    @Exposed
    final JLabel lastNameLabel = new JLabel();

    @Exposed
    final JLabel birthLabel = new JLabel();

    @Exposed
    final AddressPanel home;

    @Exposed
    final AddressPanel office;

    private final JLabel stepLabel = new JLabel();
    private final JTextField firstName = new JTextField(NAME_COLUMNS);
    private final JTextField lastName = new JTextField(NAME_COLUMNS);
    private final JTextField birth = new JTextField(NAME_COLUMNS);
    private final Contact contact;
    private final Ui ui;
    private final AddressBookMessages messages;
    private final WizardSteps wizard;
    private final List<String> names;
    private final CardLayout cards = new CardLayout();
    private final JPanel steps = new JPanel(cards);

    @Inject
    ContactWizardDialog(@Assisted Contact contact, Ui ui, AddressBookMessages messages, WizardSteps wizard) {
        this.contact = contact;
        this.ui = ui;
        this.messages = messages;
        this.wizard = wizard;
        this.home = new AddressPanel(messages);
        this.office = new AddressPanel(messages);
        this.names = List.of(messages.contactStep(), messages.homeAddressStep(), messages.officeAddressStep());
        wizard.count().set(names.size());
    }

    @Override
    public JComponent content() {
        JPanel contactStep = Forms.panel();
        Forms.row(contactStep, firstNameLabel, firstName);
        Forms.row(contactStep, lastNameLabel, lastName);
        Forms.row(contactStep, birthLabel, birth);
        steps.add(contactStep, names.get(0));
        steps.add(home, names.get(1));
        steps.add(office, names.get(2));
        firstName.setText(contact.firstName());
        lastName.setText(contact.lastName());
        birth.setText(Forms.text(contact.birth()));
        home.setAddress(contact.home());
        office.setAddress(contact.office());
        stepLabel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        ui.bind(
                index -> {
                    stepLabel.setText(messages.wizardStep(index + 1, names.size(), names.get(index)));
                    cards.show(steps, names.get(index));
                },
                wizard.step());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(stepLabel, BorderLayout.PAGE_START);
        panel.add(steps, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands standard) {
        List<BoundCommand> buttons = new ArrayList<>(wizard.buttons());
        buttons.add(standard.ok());
        buttons.add(standard.cancel());
        return buttons;
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(contact.withIdentity(firstName.getText(), lastName.getText(), Forms.date(birth))
                .withAddresses(home.address(), office.address()));
    }
}
