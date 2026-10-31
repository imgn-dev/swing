package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The dialog that creates or modifies a contact in one form: Guts' {@code ContactDetailPanel}. */
public final class ContactDialog implements DialogView<Contact> {

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

    private final ContactForm form;

    @Inject
    ContactDialog(@Assisted Contact contact, AddressBookMessages messages) {
        this.form = new ContactForm(contact, messages);
        this.home = form.home();
        this.office = form.office();
    }

    @Override
    public JComponent content() {
        JPanel panel = Forms.panel();
        form.identity(panel, firstNameLabel, lastNameLabel, birthLabel);
        Forms.wide(panel, home);
        Forms.wide(panel, office);
        return panel;
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(form.contact());
    }
}
