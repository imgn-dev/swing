package be.imgn.swing.examples.addressbook;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.inject.Inject;
import be.imgn.swing.Observable;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.ui.Column;
import be.imgn.swing.ui.Command;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Ui;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/**
 * Guts' contacts list, a view of the main window's dock: the table of the contacts, whose selection it publishes as
 * {@link ContactSelected} events, and which follows the window's {@link CurrentContact}: opened after a selection, or
 * when a picture tab becomes the active view, it selects the contact. Its {@code openPicture} command, which a double
 * click on a row runs, asks for the selected contact's picture in a tab of its own.
 */
public final class ContactListView implements DockView {

    private static final int DOUBLE_CLICK = 2;

    @Exposed
    final JTable table = new JTable();

    private final Property<Optional<Contact>> selected = Property.of(Optional.empty());
    private final AddressBookService service;
    private final AddressBookMessages messages;
    private final Ui ui;
    private final Scope scope;
    private final EventBus bus;
    private final CurrentContact current;
    private final ContactListViewCommands commands;

    @Inject
    ContactListView(
            AddressBookService service,
            AddressBookMessages messages,
            Ui ui,
            Scope scope,
            EventBus bus,
            CurrentContact current,
            ContactListViewCommands commands) {
        this.service = service;
        this.messages = messages;
        this.ui = ui;
        this.scope = scope;
        this.bus = bus;
        this.current = current;
        this.commands = commands;
    }

    @Override
    public JComponent content() {
        // Opened after a selection, it shows it.
        selected.set(current.contact().get());
        ui.bind(
                table,
                service.contacts(),
                List.of(
                        Column.of(messages::firstNameHeader, String.class, Contact::firstName),
                        Column.of(messages::lastNameHeader, String.class, Contact::lastName),
                        Column.of(
                                messages::homeCityHeader,
                                String.class,
                                contact -> contact.home().city())),
                selected,
                Contact::id);
        selected.subscribe(scope, contact -> bus.publish(new ContactSelected(contact)));
        // The selection kept for the window, which a picture tab becoming the active view changes, as a modification
        // does.
        current.contact().subscribe(scope, selected::set);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == DOUBLE_CLICK && table.rowAtPoint(event.getPoint()) >= 0) {
                    commands.openPicture().execute();
                }
            }
        });
        return new JScrollPane(table);
    }

    Observable<Boolean> hasSelection() {
        return selected.map(Optional::isPresent);
    }

    @Command(enabled = "hasSelection")
    void openPicture() {
        selected.get().ifPresent(contact -> bus.publish(new PictureRequested(contact)));
    }
}
