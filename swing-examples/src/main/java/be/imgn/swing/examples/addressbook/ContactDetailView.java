package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.ui.Converter;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.Ui;
import java.time.LocalDate;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;

/** Guts' contact detail, a view of the main window's dock: the names and birth date of the selected contact. */
public final class ContactDetailView implements DockView {

    private static final int NAME_COLUMNS = 20;

    @Exposed
    final JLabel firstNameLabel = new JLabel();

    @Exposed
    final JLabel lastNameLabel = new JLabel();

    @Exposed
    final JLabel birthLabel = new JLabel();

    private final JTextField firstName = new JTextField(NAME_COLUMNS);
    private final JTextField lastName = new JTextField(NAME_COLUMNS);
    private final JTextField birth = new JTextField(NAME_COLUMNS);
    private final CurrentContact current;
    private final Ui ui;
    private final Converter<Optional<LocalDate>> birthDate;

    @Inject
    ContactDetailView(CurrentContact current, Ui ui, AddressBookMessages messages) {
        this.current = current;
        this.ui = ui;
        this.birthDate = ContactForm.birthDate(messages);
    }

    @Override
    public JComponent content() {
        firstName.setEditable(false);
        lastName.setEditable(false);
        birth.setEditable(false);
        ui.bind(this::show, current.contact());
        return FormPanel.create()
                .row(firstNameLabel, firstName)
                .row(lastNameLabel, lastName)
                .row(birthLabel, birth)
                .build();
    }

    private void show(Optional<Contact> contact) {
        firstName.setText(contact.map(Contact::firstName).orElse(""));
        lastName.setText(contact.map(Contact::lastName).orElse(""));
        birth.setText(birthDate.format(contact.flatMap(Contact::birthDate)));
    }
}
