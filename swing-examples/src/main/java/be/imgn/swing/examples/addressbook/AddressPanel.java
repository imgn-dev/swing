package be.imgn.swing.examples.addressbook;

import java.awt.GridBagLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The fields of an address. Its labels come from the messages, as resources apply only to the fields of a view; the
 * view gives the panel its title.
 */
public final class AddressPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int STREET_COLUMNS = 32;
    private static final int ZIP_COLUMNS = 8;
    private static final int CITY_COLUMNS = 20;
    private static final int PHONE_COLUMNS = 10;

    private final JTextField street1 = new JTextField(STREET_COLUMNS);
    private final JTextField street2 = new JTextField(STREET_COLUMNS);
    private final JTextField zip = new JTextField(ZIP_COLUMNS);
    private final JTextField city = new JTextField(CITY_COLUMNS);
    private final JTextField phone = new JTextField(PHONE_COLUMNS);
    private transient String country = "";

    AddressPanel(AddressBookMessages messages) {
        super(new GridBagLayout());
        JPanel form = Forms.panel();
        Forms.row(form, new JLabel(messages.street()), street1);
        Forms.row(form, new JLabel(), street2);
        Forms.row(form, new JLabel(messages.zip()), zip);
        Forms.row(form, new JLabel(messages.city()), city);
        Forms.row(form, new JLabel(messages.phone()), phone);
        Forms.wide(this, form);
    }

    /**
     * Shows an address.
     *
     * @param address the address
     */
    void setAddress(Address address) {
        street1.setText(address.street1());
        street2.setText(address.street2());
        zip.setText(address.zip());
        city.setText(address.city());
        phone.setText(address.phone());
        country = address.country();
    }

    /**
     * Returns the address shown.
     *
     * @return the address
     */
    Address address() {
        return new Address(
                street1.getText(), street2.getText(), zip.getText(), city.getText(), country, phone.getText());
    }
}
