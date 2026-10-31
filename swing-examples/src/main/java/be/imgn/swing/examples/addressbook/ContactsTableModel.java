package be.imgn.swing.examples.addressbook;

import java.util.List;
import javax.swing.table.AbstractTableModel;

/** The contacts of the main window's table: first name, last name and home city. */
final class ContactsTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    private final transient List<String> headers;
    private transient List<Contact> contacts = List.of();

    ContactsTableModel(AddressBookMessages messages) {
        this.headers = List.of(messages.firstNameHeader(), messages.lastNameHeader(), messages.homeCityHeader());
    }

    /**
     * Replaces the contacts.
     *
     * @param newContacts the contacts
     */
    void setContacts(List<Contact> newContacts) {
        this.contacts = newContacts;
        fireTableDataChanged();
    }

    /**
     * Returns the contact of a row.
     *
     * @param row the row
     * @return the contact
     */
    Contact contact(int row) {
        return contacts.get(row);
    }

    /**
     * Returns the row of a contact.
     *
     * @param id the identifier of the contact
     * @return the row, or {@code -1} when no row shows it
     */
    int rowOf(int id) {
        for (int row = 0; row < contacts.size(); row++) {
            if (contacts.get(row).id() == id) {
                return row;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return contacts.size();
    }

    @Override
    public int getColumnCount() {
        return headers.size();
    }

    @Override
    public String getColumnName(int column) {
        return headers.get(column);
    }

    @Override
    public Object getValueAt(int row, int column) {
        Contact contact = contacts.get(row);
        return switch (column) {
            case 0 -> contact.firstName();
            case 1 -> contact.lastName();
            default -> contact.home().city();
        };
    }
}
