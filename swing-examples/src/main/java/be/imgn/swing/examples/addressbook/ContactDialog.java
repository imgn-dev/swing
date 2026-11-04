package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

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

    private final ContactForm fields;
    private final Form form;
    private final AddressBookMessages messages;

    @Inject
    ContactDialog(@Assisted Contact contact, Form form, AddressBookMessages messages) {
        this.fields = new ContactForm(contact);
        this.form = form;
        this.messages = messages;
        this.home = fields.home();
        this.office = fields.office();
    }

    @Override
    public JComponent content() {
        fields.bind(form, messages);
        return fields.identity(FormPanel.create(), firstNameLabel, lastNameLabel, birthLabel)
                .wide(home)
                .wide(office)
                .build();
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(fields.contact());
    }
}
