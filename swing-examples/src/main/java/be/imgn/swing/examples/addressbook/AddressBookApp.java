package be.imgn.swing.examples.addressbook;

import be.imgn.gaia.core.event.EventBus;
import be.imgn.gaia.core.event.Subscribe;
import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.Property;
import be.imgn.swing.Scope;
import be.imgn.swing.ui.Column;
import be.imgn.swing.ui.Converter;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.FormPanel;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The main window of the address book: the list of contacts, above the details of the selected one, beside its picture.
 * Guts' demo builds them as three views; a single-frame application has one view, so their components are fields of the
 * application class. As in Guts, the list publishes the selected contact as a {@link ContactSelected} event, which the
 * details and the picture consume.
 */
@Application(name = "AddressBook", vendor = "jfpoilpret")
public final class AddressBookApp implements SingleFrame {

    private static final int NAME_COLUMNS = 20;
    private static final int IMAGE_SIZE = 200;

    @Exposed
    final JTable table = new JTable();

    @Exposed
    final JLabel firstNameLabel = new JLabel();

    @Exposed
    final JLabel lastNameLabel = new JLabel();

    @Exposed
    final JLabel birthLabel = new JLabel();

    @Exposed
    final JLabel picture = new JLabel("", SwingConstants.CENTER);

    @Exposed
    final JSplitPane secondarySplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

    @Exposed
    final JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

    private final JTextField firstName = new JTextField(NAME_COLUMNS);
    private final JTextField lastName = new JTextField(NAME_COLUMNS);
    private final JTextField birth = new JTextField(NAME_COLUMNS);
    private final Property<Optional<Contact>> selected = Property.of(Optional.empty());
    private final AddressBookService service;
    private final AddressBookMenuBar menuBar;
    private final Ui ui;
    private final Scope scope;
    private final EventBus bus;
    private final AddressBookMessages messages;
    private final Converter<Optional<LocalDate>> birthDate;

    @Inject
    AddressBookApp(
            AddressBookService service,
            AddressBookMenuBar menuBar,
            Ui ui,
            Scope scope,
            EventBus bus,
            AddressBookMessages messages) {
        this.service = service;
        this.menuBar = menuBar;
        this.ui = ui;
        this.scope = scope;
        this.bus = bus;
        this.messages = messages;
        this.birthDate = ContactForm.birthDate(messages);
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
        ui.bind(
                table,
                service.contacts(),
                List.of(
                        Column.of(messages.firstNameHeader(), String.class, Contact::firstName),
                        Column.of(messages.lastNameHeader(), String.class, Contact::lastName),
                        Column.of(
                                messages.homeCityHeader(),
                                String.class,
                                contact -> contact.home().city())),
                selected,
                Contact::id);
        selected.subscribe(scope, contact -> bus.publish(new ContactSelected(contact)));
        secondarySplit.setTopComponent(new JScrollPane(table));
        secondarySplit.setBottomComponent(detail());
        picture.setMinimumSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        picture.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        picture.setText(messages.noContactSelected());
        mainSplit.setLeftComponent(secondarySplit);
        mainSplit.setRightComponent(picture);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(mainSplit, BorderLayout.CENTER);
        return panel;
    }

    private JComponent detail() {
        firstName.setEditable(false);
        lastName.setEditable(false);
        birth.setEditable(false);
        return FormPanel.create()
                .row(firstNameLabel, firstName)
                .row(lastNameLabel, lastName)
                .row(birthLabel, birth)
                .build();
    }

    @Subscribe
    void showDetail(ContactSelected event) {
        var contact = event.contact();
        firstName.setText(contact.map(Contact::firstName).orElse(""));
        lastName.setText(contact.map(Contact::lastName).orElse(""));
        birth.setText(birthDate.format(contact.flatMap(Contact::birthDate)));
    }

    @Subscribe
    void showPicture(ContactSelected event) {
        var contact = event.contact();
        var icon = contact.flatMap(selection -> service.contactPicture(selection.id()));
        picture.setIcon(icon.orElse(null));
        if (contact.isEmpty()) {
            picture.setText(messages.noContactSelected());
        } else {
            picture.setText(icon.isPresent() ? "" : messages.noPicture());
        }
    }

    @Override
    public JMenuBar menuBar() {
        return menuBar.create();
    }
}
