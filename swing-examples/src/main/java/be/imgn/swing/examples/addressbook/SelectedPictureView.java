package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.docking.DockView;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Ui;
import java.awt.Dimension;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * The picture of the selected contact, a view of the main window's dock: Guts' picture area holds the contacts' picture
 * tabs only, and this view keeps the picture of the selection that milestone 4's window showed.
 */
public final class SelectedPictureView implements DockView {

    private static final int IMAGE_SIZE = 200;

    @Exposed
    final JLabel picture = new JLabel("", SwingConstants.CENTER);

    private final CurrentContact current;
    private final AddressBookService service;
    private final AddressBookMessages messages;
    private final Ui ui;

    @Inject
    SelectedPictureView(CurrentContact current, AddressBookService service, AddressBookMessages messages, Ui ui) {
        this.current = current;
        this.service = service;
        this.messages = messages;
        this.ui = ui;
    }

    @Override
    public JComponent content() {
        picture.setMinimumSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        picture.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        // Again at every refresh, so that the text follows the locale.
        ui.render(this::show, current.contact());
        return picture;
    }

    private void show() {
        Optional<Contact> contact = current.contact().get();
        picture.setIcon(
                contact.flatMap(shown -> service.contactPicture(shown.id())).orElse(null));
        if (contact.isEmpty()) {
            picture.setText(messages.noContactSelected());
        } else {
            picture.setText(picture.getIcon() != null ? "" : messages.noPicture());
        }
    }
}
