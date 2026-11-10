package be.imgn.swing.examples.addressbook;

import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Scope;
import be.imgn.swing.docking.Dock;
import be.imgn.swing.docking.DockRegion;
import be.imgn.swing.docking.Placement;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenuBar;

/**
 * The main window of the address book, docked as in Guts' docking demo: the contacts list, the selected contact's
 * detail below it, and its picture beside them, each a view of the window's dock, which the user rearranges and the
 * Views menu opens again. The list publishes the selected contact as a {@link ContactSelected} event; a contact's
 * picture opens in a tab of its own beside the selected one's.
 */
@Application(name = "AddressBook", vendor = "jfpoilpret")
public final class AddressBookApp implements SingleFrame {

    /** The placement of the selected contact's picture in the first layout: it fills the empty dock. */
    static final Placement PICTURE = Placement.tab();

    /** The placement of the contacts list in the first layout: west of the picture. */
    static final Placement LIST = Placement.of(DockRegion.WEST, 0.33);

    /** The placement of the contact detail in the first layout: south of the list. */
    static final Placement DETAIL = Placement.beside(ContactListView.class, DockRegion.SOUTH, 0.5);

    private final Dock dock;
    private final CurrentContact current;
    private final AddressBookMenuBar menuBar;
    private final Scope scope;

    @Inject
    AddressBookApp(Dock dock, CurrentContact current, AddressBookMenuBar menuBar, Scope scope) {
        this.dock = dock;
        this.current = current;
        this.menuBar = menuBar;
        this.scope = scope;
    }

    /**
     * Launches the address book.
     *
     * @param args the arguments
     */
    public static void main(String[] args) {
        Swing.launch(AddressBookApp.class, args);
    }

    @Override
    public JComponent content() {
        // A contact selected in the list selects its picture tab, if open, leaving the active view as it is.
        current.contact()
                .subscribe(scope, contact -> contact.flatMap(this::pictureOf).ifPresent(dock::select));
        // A picture tab that becomes the active view selects its contact, as the service holds it now.
        dock.active()
                .subscribe(
                        scope,
                        active -> active.filter(ContactPictureView.class::isInstance)
                                .map(view ->
                                        ((ContactPictureView) view).contact().id())
                                .ifPresent(current::select));
        return dock.area(opened -> {
            opened.open(SelectedPictureView.class, PICTURE);
            opened.open(ContactListView.class, LIST);
            opened.open(ContactDetailView.class, DETAIL);
        });
    }

    private Optional<ContactPictureView> pictureOf(Contact contact) {
        return dock.views().stream()
                .filter(ContactPictureView.class::isInstance)
                .map(ContactPictureView.class::cast)
                .filter(view -> view.contact().id() == contact.id())
                .findFirst();
    }

    @Subscribe
    void openPicture(PictureRequested event) {
        Contact contact = event.contact();
        dock.open(
                ContactPictureViewFactory.class,
                factory -> factory.create(contact),
                Integer.toString(contact.id()),
                Placement.tabWith(SelectedPictureView.class));
    }

    @Override
    public JMenuBar menuBar() {
        return menuBar.create();
    }
}
