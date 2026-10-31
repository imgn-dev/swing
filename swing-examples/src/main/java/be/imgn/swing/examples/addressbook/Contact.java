package be.imgn.swing.examples.addressbook;

import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A contact of the address book.
 *
 * @param id the identifier, {@code 0} for a contact not yet created
 * @param firstName the first name
 * @param lastName the last name
 * @param birth the birth date, if known
 * @param home the home address
 * @param office the office address
 */
public record Contact(
        int id, String firstName, String lastName, @Nullable LocalDate birth, Address home, Address office) {

    /** The contact a creation starts from. */
    public static final Contact NEW = new Contact(0, "", "", null, Address.EMPTY, Address.EMPTY);

    /**
     * Returns the birth date.
     *
     * @return the birth date, if known
     */
    public Optional<LocalDate> birthDate() {
        return Optional.ofNullable(birth);
    }

    /**
     * Returns whether the contact is not yet created.
     *
     * @return {@code true} for a new contact
     */
    public boolean isNew() {
        return id == 0;
    }

    /**
     * Returns the contact with another identifier.
     *
     * @param newId the identifier
     * @return the contact
     */
    public Contact withId(int newId) {
        return new Contact(newId, firstName, lastName, birth, home, office);
    }

    /**
     * Returns the contact with other names and birth date.
     *
     * @param newFirstName the first name
     * @param newLastName the last name
     * @param newBirth the birth date, if known
     * @return the contact
     */
    public Contact withIdentity(String newFirstName, String newLastName, @Nullable LocalDate newBirth) {
        return new Contact(id, newFirstName, newLastName, newBirth, home, office);
    }

    /**
     * Returns the contact with other addresses.
     *
     * @param newHome the home address
     * @param newOffice the office address
     * @return the contact
     */
    public Contact withAddresses(Address newHome, Address newOffice) {
        return new Contact(id, firstName, lastName, birth, newHome, newOffice);
    }
}
