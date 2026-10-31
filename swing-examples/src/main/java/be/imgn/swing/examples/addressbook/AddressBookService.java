package be.imgn.swing.examples.addressbook;

import be.imgn.swing.Observable;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;

/** The contacts of the address book. Its methods may be called from any thread. */
public interface AddressBookService {

    /**
     * Returns the contacts, in order of creation.
     *
     * @return the observable contacts
     */
    Observable<List<Contact>> contacts();

    /**
     * Creates a contact.
     *
     * @param contact the contact, whose identifier is ignored
     * @return the created contact, with its identifier
     */
    Contact createContact(Contact contact);

    /**
     * Replaces the contact of the same identifier.
     *
     * @param contact the contact
     */
    void modifyContact(Contact contact);

    /**
     * Removes the contact of the same identifier.
     *
     * @param contact the contact
     */
    void removeContact(Contact contact);

    /**
     * Returns the picture of a contact.
     *
     * @param id the identifier of the contact
     * @return the picture, if the contact has one
     */
    Optional<Icon> contactPicture(int id);
}
