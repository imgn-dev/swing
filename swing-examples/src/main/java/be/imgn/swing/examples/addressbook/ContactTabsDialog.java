package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.ui.DialogView;
import be.imgn.swing.ui.Exposed;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * The dialog that creates or modifies a contact in three tabs: its information, its home address and its office
 * address. Guts' {@code ContactDetailTabPanel}.
 */
public final class ContactTabsDialog implements DialogView<Contact> {

    @Exposed
    final JTabbedPane tabs = new JTabbedPane();

    @Exposed
    final JPanel contactTab = Forms.panel();

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
    ContactTabsDialog(@Assisted Contact contact, AddressBookMessages messages) {
        this.form = new ContactForm(contact, messages);
        this.home = form.home();
        this.office = form.office();
    }

    @Override
    public JComponent content() {
        form.identity(contactTab, firstNameLabel, lastNameLabel, birthLabel);
        tabs.addTab("", contactTab);
        tabs.addTab("", home);
        tabs.addTab("", office);
        return tabs;
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(form.contact());
    }
}
