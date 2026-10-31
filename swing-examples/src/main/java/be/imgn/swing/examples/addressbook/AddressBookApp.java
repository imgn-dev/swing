package be.imgn.swing.examples.addressbook;

import be.imgn.inject.Inject;
import be.imgn.swing.Application;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.SingleFrame;
import be.imgn.swing.ui.Swing;
import be.imgn.swing.ui.Ui;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

/**
 * The main window of the address book: the list of contacts, above the details of the selected one, beside its picture.
 * Guts' demo builds them as three views; in milestone 1, a single-frame application has one view, so their components
 * are fields of the application class.
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
    private final AddressBookService service;
    private final ContactSelection selection;
    private final AddressBookMenuBar menuBar;
    private final Ui ui;
    private final AddressBookMessages messages;
    private final ContactsTableModel model;

    @Inject
    AddressBookApp(
            AddressBookService service,
            ContactSelection selection,
            AddressBookMenuBar menuBar,
            Ui ui,
            AddressBookMessages messages) {
        this.service = service;
        this.selection = selection;
        this.menuBar = menuBar;
        this.ui = ui;
        this.messages = messages;
        this.model = new ContactsTableModel(messages);
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
        table.setModel(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                selection.contact().set(row < 0 ? Optional.empty() : Optional.of(model.contact(row)));
            }
        });
        ui.bind(this::showContacts, service.contacts());
        ui.bind(this::showContact, selection.contact());
        secondarySplit.setTopComponent(new JScrollPane(table));
        secondarySplit.setBottomComponent(detail());
        picture.setMinimumSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
        picture.setPreferredSize(new Dimension(IMAGE_SIZE, IMAGE_SIZE));
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
        JPanel form = Forms.panel();
        Forms.row(form, firstNameLabel, firstName);
        Forms.row(form, lastNameLabel, lastName);
        Forms.row(form, birthLabel, birth);
        return form;
    }

    // Keeps the selected contact selected, with its new values, when the contacts change.
    private void showContacts(List<Contact> all) {
        Optional<Contact> selected = selection.contact().get();
        model.setContacts(all);
        int row = selected.map(contact -> model.rowOf(contact.id())).orElse(-1);
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        } else {
            selection.contact().set(Optional.empty());
        }
    }

    private void showContact(Optional<Contact> selected) {
        firstName.setText(selected.map(Contact::firstName).orElse(""));
        lastName.setText(selected.map(Contact::lastName).orElse(""));
        birth.setText(selected.map(contact -> Forms.text(contact.birth())).orElse(""));
        Optional<Icon> icon = selected.flatMap(contact -> service.contactPicture(contact.id()));
        picture.setIcon(icon.orElse(null));
        if (selected.isEmpty()) {
            picture.setText(messages.noContactSelected());
        } else {
            picture.setText(icon.isPresent() ? "" : messages.noPicture());
        }
    }

    @Override
    public JMenuBar menuBar() {
        return menuBar.create();
    }

    @Override
    public void configureFrame(JFrame frame) {
        URL icon = Objects.requireNonNull(AddressBookApp.class.getResource("icons/main.png"), "icons/main.png");
        frame.setIconImage(new ImageIcon(icon).getImage());
    }
}
