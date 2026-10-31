package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The dialog that creates or modifies a contact in three steps, with previous and next buttons: Guts'
 * {@code ContactDetailWizardController}, whose wizard comes from Guts' {@code WizardModule}, and here from the dialog's
 * own commands. Its {@code ok()} finishes the wizard from any step.
 */
public final class ContactWizardDialog implements DialogView<Contact> {

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

    @Exposed
    final Observable<Boolean> hasPrevious;

    @Exposed
    final Observable<Boolean> hasNext;

    private final JLabel stepLabel = new JLabel();
    private final ContactForm form;
    private final Ui ui;
    private final AddressBookMessages messages;
    private final ContactWizardDialogCommands commands;
    private final Property<Integer> step = Property.of(0);
    private final List<String> names;
    private final CardLayout cards = new CardLayout();
    private final JPanel steps = new JPanel(cards);

    @Inject
    ContactWizardDialog(
            @Assisted Contact contact, Ui ui, AddressBookMessages messages, ContactWizardDialogCommands commands) {
        this.form = new ContactForm(contact, messages);
        this.ui = ui;
        this.messages = messages;
        this.commands = commands;
        this.home = form.home();
        this.office = form.office();
        this.names = List.of(messages.contactStep(), messages.homeAddressStep(), messages.officeAddressStep());
        this.hasPrevious = step.map(index -> index > 0);
        this.hasNext = step.map(index -> index < names.size() - 1);
    }

    @Override
    public JComponent content() {
        JPanel contactStep = Forms.panel();
        form.identity(contactStep, firstNameLabel, lastNameLabel, birthLabel);
        steps.add(contactStep, names.get(0));
        steps.add(home, names.get(1));
        steps.add(office, names.get(2));
        stepLabel.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        ui.bind(
                index -> {
                    stepLabel.setText(messages.wizardStep(index + 1, names.size(), names.get(index)));
                    cards.show(steps, names.get(index));
                },
                step);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(stepLabel, BorderLayout.PAGE_START);
        panel.add(steps, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands standard) {
        return List.of(commands.previous(), commands.next(), standard.ok(), standard.cancel());
    }

    @Command(enabled = "hasPrevious")
    void previous() {
        step.set(step.get() - 1);
    }

    @Command(enabled = "hasNext")
    void next() {
        step.set(step.get() + 1);
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(form.contact());
    }
}
