package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Assisted;
import be.imgn.inject.Inject;
import be.imgn.swing.Property;
import be.imgn.swing.Task;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.docking.Docked;
import be.imgn.swing.ui.Block;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Ui;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The picture of one contact, in a tab of its own, as Guts' picture tabs: a view of the main window's dock, created
 * through its factory for the contact, titled with the contact's last name. Its picture loads in a task.
 */
public final class ContactPictureView implements DockView {

    @Exposed
    final JLabel picture = new JLabel("", SwingConstants.CENTER);

    private final Property<Optional<Icon>> icon = Property.of(Optional.empty());
    private final Property<Boolean> loaded = Property.of(false);
    private final Contact contact;
    private final AddressBookService service;
    private final AddressBookMessages messages;
    private final Ui ui;

    @Inject
    ContactPictureView(
            @Assisted Contact contact, Docked docked, AddressBookService service, AddressBookMessages messages, Ui ui) {
        this.contact = contact;
        this.service = service;
        this.messages = messages;
        this.ui = ui;
        docked.setTitle(contact.lastName());
    }

    @Override
    public JComponent content() {
        ui.run(
                Task.of(() -> service.contactPicture(contact.id())).onSuccess(found -> {
                    icon.set(found);
                    loaded.set(true);
                }),
                Block.NONE);
        // Again at every refresh, so that the text follows the locale.
        ui.render(this::show, loaded);
        return picture;
    }

    private void show() {
        picture.setIcon(icon.get().orElse(null));
        picture.setText(!loaded.get() || icon.get().isPresent() ? "" : messages.noPicture());
    }

    /**
     * Returns the contact whose picture this shows.
     *
     * @return the contact
     */
    Contact contact() {
        return contact;
    }
}
