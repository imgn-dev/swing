package be.imgn.swing.examples.addressbook;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The fields of a contact, shown from the contact they edit, and the contact they read back: what the three contact
 * dialogs share. Each dialog lays the fields out its own way, and keeps the labels and the address panels as its own
 * fields, since resources apply only to the fields of a view.
 */
final class ContactForm {

    private static final int NAME_COLUMNS = 20;

    private final JTextField firstName = new JTextField(NAME_COLUMNS);
    private final JTextField lastName = new JTextField(NAME_COLUMNS);
    private final JTextField birth = new JTextField(NAME_COLUMNS);
    private final AddressPanel home;
    private final AddressPanel office;
    private final Contact contact;

    ContactForm(Contact contact, AddressBookMessages messages) {
        this.contact = contact;
        this.home = new AddressPanel(messages);
        this.office = new AddressPanel(messages);
        firstName.setText(contact.firstName());
        lastName.setText(contact.lastName());
        birth.setText(Forms.text(contact.birth()));
        home.setAddress(contact.home());
        office.setAddress(contact.office());
    }

    /**
     * Adds the rows of the contact's names and birth date to a form.
     *
     * @param form the form
     * @param firstNameLabel the label of the first name
     * @param lastNameLabel the label of the last name
     * @param birthLabel the label of the birth date
     */
    void identity(JPanel form, JLabel firstNameLabel, JLabel lastNameLabel, JLabel birthLabel) {
        Forms.row(form, firstNameLabel, firstName);
        Forms.row(form, lastNameLabel, lastName);
        Forms.row(form, birthLabel, birth);
    }

    /**
     * Returns the panel of the home address.
     *
     * @return the panel
     */
    AddressPanel home() {
        return home;
    }

    /**
     * Returns the panel of the office address.
     *
     * @return the panel
     */
    AddressPanel office() {
        return office;
    }

    /**
     * Returns the contact as the fields show it.
     *
     * @return the contact
     */
    Contact contact() {
        return contact.withIdentity(firstName.getText(), lastName.getText(), Forms.date(birth))
                .withAddresses(home.address(), office.address());
    }
}
