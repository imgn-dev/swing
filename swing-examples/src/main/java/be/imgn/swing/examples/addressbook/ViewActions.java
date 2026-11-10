package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.docking.Dock;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.ViewScoped;

/**
 * The commands of the Views menu, as in Guts' docking demo: each opens its view with the placement of the first layout,
 * or brings it to the front when it is open.
 */
@ViewScoped
public final class ViewActions {

    private final Dock dock;

    @Inject
    ViewActions(Dock dock) {
        this.dock = dock;
    }

    @Command
    void showContactList() {
        dock.open(ContactListView.class, AddressBookApp.LIST);
    }

    @Command
    void showContactDetail() {
        dock.open(ContactDetailView.class, AddressBookApp.DETAIL);
    }

    @Command
    void showContactPicture() {
        dock.open(SelectedPictureView.class, AddressBookApp.PICTURE);
    }
}
