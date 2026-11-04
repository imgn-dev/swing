package be.imgn.swing.examples.addressbook;

import be.imgn.swing.Property;
import be.imgn.swing.Rule;
import be.imgn.swing.ui.Converter;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import java.time.LocalDate;
import java.util.Optional;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * The fields of a contact, bound in a dialog's form to the values of the contact they edit, and the contact they read
 * back: what the three contact dialogs share. Each dialog lays the fields out its own way, and keeps the labels and the
 * address panels as its own fields, so that its resources reach them.
 */
final class ContactForm {

    private static final int NAME_COLUMNS = 20;

    private final JTextField firstNameField = new JTextField(NAME_COLUMNS);
    private final JTextField lastNameField = new JTextField(NAME_COLUMNS);
    private final JTextField birthField = new JTextField(NAME_COLUMNS);
    private final Property<String> firstName;
    private final Property<String> lastName;
    private final Property<Optional<LocalDate>> birth;
    private final AddressPanel home = new AddressPanel();
    private final AddressPanel office = new AddressPanel();
    private final Contact contact;

    ContactForm(Contact contact) {
        this.contact = contact;
        this.firstName = Property.of(contact.firstName());
        this.lastName = Property.of(contact.lastName());
        this.birth = Property.of(contact.birthDate());
        home.setAddress(contact.home());
        office.setAddress(contact.office());
    }

    /**
     * Returns the converter of birth dates: an empty text for no date, or an ISO date such as {@code 1815-12-10}.
     *
     * @param messages the messages, which give its requirement
     * @return the converter
     */
    static Converter<Optional<LocalDate>> birthDate(AddressBookMessages messages) {
        return Converter.optional(Converter.of(LocalDate::parse, LocalDate::toString, messages.isoDate()));
    }

    /**
     * Binds every field in a form: the first and last names are required, and the birth date is empty or an ISO date.
     *
     * @param form the form
     * @param messages the messages, which give the requirements
     */
    void bind(Form form, AddressBookMessages messages) {
        form.bind(firstNameField, firstName).check(Rule.required(messages.firstNameRequired()));
        form.bind(lastNameField, lastName).check(Rule.required(messages.lastNameRequired()));
        form.bind(birthField, birth, birthDate(messages));
        home.bind(form);
        office.bind(form);
    }

    /**
     * Adds the rows of the contact's names and birth date to a form panel.
     *
     * @param panel the form panel
     * @param firstNameLabel the label of the first name
     * @param lastNameLabel the label of the last name
     * @param birthLabel the label of the birth date
     * @return the form panel
     */
    FormPanel identity(FormPanel panel, JLabel firstNameLabel, JLabel lastNameLabel, JLabel birthLabel) {
        return panel.row(firstNameLabel, firstNameField)
                .row(lastNameLabel, lastNameField)
                .row(birthLabel, birthField);
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
     * Returns the contact as the form's values give it.
     *
     * @return the contact
     */
    Contact contact() {
        return contact.withIdentity(firstName.get(), lastName.get(), birth.get().orElse(null))
                .withAddresses(home.address(), office.address());
    }
}
