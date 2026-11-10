package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.BoundCommand;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.StandardCommands;
import be.imgn.swing.ui.Ui;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The dialog that creates or modifies a contact in three tabs: its information, its home address and its office
 * address. Guts' {@code ContactDetailTabPanel}. When it modifies a contact, its Apply button modifies the contact in
 * the service and keeps the dialog open; creating a contact has no Apply.
 */
public final class ContactTabsDialog implements DialogView<Contact> {

    @Exposed
    final JTabbedPane tabs = new JTabbedPane();

    @Exposed
    final JLabel firstNameLabel = new JLabel();

    @Exposed
    final JLabel lastNameLabel = new JLabel();

    @Exposed
    final JLabel birthLabel = new JLabel();

    @Exposed
    final JPanel contactTab;

    @Exposed
    final AddressPanel home;

    @Exposed
    final AddressPanel office;

    private final Contact contact;
    private final ContactForm fields;
    private final Form form;
    private final AddressBookService service;
    private final AddressBookMessages messages;
    private final Ui ui;

    @Inject
    ContactTabsDialog(
            @Assisted Contact contact, Form form, AddressBookService service, AddressBookMessages messages, Ui ui) {
        this.contact = contact;
        this.fields = new ContactForm(contact);
        this.form = form;
        this.service = service;
        this.messages = messages;
        this.ui = ui;
        this.contactTab = fields.identity(FormPanel.create(), firstNameLabel, lastNameLabel, birthLabel)
                .build();
        this.home = fields.home();
        this.office = fields.office();
    }

    @Override
    public JComponent content() {
        fields.bind(form, messages);
        ui.addTab(tabs, contactTab);
        ui.addTab(tabs, home);
        ui.addTab(tabs, office);
        return tabs;
    }

    @Override
    public List<BoundCommand> buttons(StandardCommands commands) {
        return contact.isNew()
                ? List.of(commands.ok(), commands.cancel())
                : List.of(commands.ok(), commands.cancel(), commands.apply());
    }

    // A new contact is created from the result, once the dialog closes.
    @Override
    public void commit() {
        if (!contact.isNew()) {
            service.modifyContact(fields.contact());
        }
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(fields.contact());
    }
}
