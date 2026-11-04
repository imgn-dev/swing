package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.ObservableList;
import java.net.URL;
import java.time.LocalDate;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** The contacts, in memory, starting with a few hard-coded ones. */
@Singleton
public final class AddressBookServiceImpl implements AddressBookService {

    private final ObservableList<Contact> contacts = ObservableList.of();
    private int next;

    @Inject
    AddressBookServiceImpl() {
        createContact(create("John", "Smith", "1966-09-24", ""));
        createContact(create("Phoebe", "Smith", "1978-04-04", ""));
        createContact(create("Luke", "Smith", "2003-06-04", ""));
        createContact(create("Linda", "Doe", "1960-10-27", "San Francisco"));
        createContact(create("Mary", "Doe", "1964-05-04", "New York"));
    }

    private static Contact create(String firstName, String lastName, String birth, String city) {
        return new Contact(0, firstName, lastName, LocalDate.parse(birth), Address.ofCity(city), Address.EMPTY);
    }

    @Override
    public ObservableList<Contact> contacts() {
        return contacts;
    }

    @Override
    public synchronized Contact createContact(Contact contact) {
        Contact created = contact.withId(++next);
        contacts.add(created);
        return created;
    }

    // Synchronized, as every modification is, so that none moves the contact between finding and changing it.
    @Override
    public synchronized void modifyContact(Contact contact) {
        int index = indexOf(contact.id());
        if (index >= 0) {
            contacts.set(index, contact);
        }
    }

    @Override
    public synchronized void removeContact(Contact contact) {
        int index = indexOf(contact.id());
        if (index >= 0) {
            contacts.remove(index);
        }
    }

    @Override
    public Optional<Icon> contactPicture(int id) {
        URL url = AddressBookServiceImpl.class.getResource("contacts/" + id + ".jpg");
        return url == null ? Optional.empty() : Optional.of(new ImageIcon(url));
    }

    private int indexOf(int id) {
        for (int index = 0; index < contacts.size(); index++) {
            if (contacts.get(index).id() == id) {
                return index;
            }
        }
        return -1;
    }
}
