package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.ui.ViewScoped;
import java.util.Optional;

/**
 * The contact selected in the list of the main window. In Guts, the list publishes the selection on an event channel
 * that the views and actions consume; here, they observe it.
 */
@ViewScoped
public final class ContactSelection {

    private final Property<Optional<Contact>> contact = Property.of(Optional.empty());

    @Inject
    ContactSelection() {}

    /**
     * Returns the selected contact.
     *
     * @return the property of the selected contact, empty when none is
     */
    public Property<Optional<Contact>> contact() {
        return contact;
    }

    /**
     * Returns whether a contact is selected.
     *
     * @return the observable state
     */
    public Observable<Boolean> isSelected() {
        return contact.map(Optional::isPresent);
    }
}
