package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.inject.Singleton;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import java.net.URL;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;

/** The contacts, in memory, starting with a few hard-coded ones. */
@Singleton
public final class AddressBookServiceImpl implements AddressBookService {

    private final Property<List<Contact>> contacts = Property.of(List.of());
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
    public Observable<List<Contact>> contacts() {
        return contacts;
    }

    @Override
    public synchronized Contact createContact(Contact contact) {
        Contact created = contact.withId(++next);
        List<Contact> all = new ArrayList<>(contacts.get());
        all.add(created);
        contacts.set(List.copyOf(all));
        return created;
    }

    @Override
    public synchronized void modifyContact(Contact contact) {
        contacts.set(contacts.get().stream()
                .map(existing -> existing.id() == contact.id() ? contact : existing)
                .toList());
    }

    @Override
    public synchronized void removeContact(Contact contact) {
        contacts.set(contacts.get().stream()
                .filter(existing -> existing.id() != contact.id())
                .toList());
    }

    @Override
    public Optional<Icon> contactPicture(int id) {
        URL url = AddressBookServiceImpl.class.getResource("contacts/" + id + ".jpg");
        return url == null ? Optional.empty() : Optional.of(new ImageIcon(url));
    }
}
