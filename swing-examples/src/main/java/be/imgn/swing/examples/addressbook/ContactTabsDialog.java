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
import javax.swing.JTextField;

/**
 * The dialog that creates or modifies a contact in three tabs: its information, its home address and its office
 * address. Guts' {@code ContactDetailTabPanel}.
 */
public final class ContactTabsDialog implements DialogView<Contact> {

    private static final int NAME_COLUMNS = 20;

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

    private final JTextField firstName = new JTextField(NAME_COLUMNS);
    private final JTextField lastName = new JTextField(NAME_COLUMNS);
    private final JTextField birth = new JTextField(NAME_COLUMNS);
    private final Contact contact;

    @Inject
    ContactTabsDialog(@Assisted Contact contact, AddressBookMessages messages) {
        this.contact = contact;
        this.home = new AddressPanel(messages);
        this.office = new AddressPanel(messages);
    }

    @Override
    public JComponent content() {
        Forms.row(contactTab, firstNameLabel, firstName);
        Forms.row(contactTab, lastNameLabel, lastName);
        Forms.row(contactTab, birthLabel, birth);
        tabs.addTab("", contactTab);
        tabs.addTab("", home);
        tabs.addTab("", office);
        firstName.setText(contact.firstName());
        lastName.setText(contact.lastName());
        birth.setText(Forms.text(contact.birth()));
        home.setAddress(contact.home());
        office.setAddress(contact.office());
        return tabs;
    }

    @Override
    public Optional<Contact> result() {
        return Optional.of(contact.withIdentity(firstName.getText(), lastName.getText(), Forms.date(birth))
                .withAddresses(home.address(), office.address()));
    }
}
