package be.imgn.swing.examples.addressbook;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.UiExecutor;
import be.imgn.swing.ui.WindowScoped;
import java.util.Optional;

/**
 * The contact selected in the contacts list, kept for the views of the main window: they are opened and closed while it
 * is open, and one opened after a selection shows it. It follows the {@link ContactSelected} events, in which the
 * selection is still published, and the contacts of the service, so that a contact modified or removed while the list
 * is closed is shown as it is.
 */
@WindowScoped
public final class CurrentContact {

    private final Property<Optional<Contact>> contact = Property.of(Optional.empty());
    private final AddressBookService service;
    private final EventBus bus;

    @Inject
    CurrentContact(AddressBookService service, EventBus bus, UiExecutor executor, Scope scope) {
        this.service = service;
        this.bus = bus;
        // The contacts change on the threads of tasks: the views follow on the EDT.
        service.contacts().subscribe(scope, contacts -> executor.execute(this::follow));
    }

    /**
     * Returns the selected contact.
     *
     * @return the contact, empty before any is selected and while none is
     */
    public Observable<Optional<Contact>> contact() {
        return contact;
    }

    /**
     * Selects a contact, as of the service's contacts now, as selecting it in the list does: it publishes the
     * selection.
     *
     * @param id the identifier of the contact
     */
    void select(int id) {
        bus.publish(new ContactSelected(find(id)));
    }

    @Subscribe
    void selected(ContactSelected event) {
        contact.set(event.contact());
    }

    // The selected contact as the service holds it now, or none once it is removed.
    private void follow() {
        Optional<Contact> selected = contact.get();
        if (selected.isPresent()) {
            Optional<Contact> now = find(selected.get().id());
            if (!now.equals(selected)) {
                bus.publish(new ContactSelected(now));
            }
        }
    }

    private Optional<Contact> find(int id) {
        return service.contacts().get().stream()
                .filter(candidate -> candidate.id() == id)
                .findFirst();
    }
}
