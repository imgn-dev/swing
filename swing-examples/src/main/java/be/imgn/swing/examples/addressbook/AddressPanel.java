package be.imgn.swing.examples.addressbook;

import be.imgn.swing.Property;
import be.imgn.swing.ui.Exposed;
import be.imgn.swing.ui.Form;
import be.imgn.swing.ui.FormPanel;
import java.awt.BorderLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * The fields of an address. The view that holds it sets its title and the texts of its labels, through nested tables of
 * its resources such as {@code [home.streetLabel]}, so two views may name its labels differently.
 */
public final class AddressPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int STREET_COLUMNS = 32;
    private static final int ZIP_COLUMNS = 8;
    private static final int CITY_COLUMNS = 20;
    private static final int PHONE_COLUMNS = 10;

    @Exposed
    final JLabel streetLabel = new JLabel();

    @Exposed
    final JLabel zipLabel = new JLabel();

    @Exposed
    final JLabel cityLabel = new JLabel();

    @Exposed
    final JLabel phoneLabel = new JLabel();

    private final JTextField street1Field = new JTextField(STREET_COLUMNS);
    private final JTextField street2Field = new JTextField(STREET_COLUMNS);
    private final JTextField zipField = new JTextField(ZIP_COLUMNS);
    private final JTextField cityField = new JTextField(CITY_COLUMNS);
    private final JTextField phoneField = new JTextField(PHONE_COLUMNS);
    private final transient Property<String> street1 = Property.of("");
    private final transient Property<String> street2 = Property.of("");
    private final transient Property<String> zip = Property.of("");
    private final transient Property<String> city = Property.of("");
    private final transient Property<String> phone = Property.of("");
    private transient String country = "";

    AddressPanel() {
        super(new BorderLayout());
        add(
                FormPanel.create()
                        .row(streetLabel, street1Field)
                        // The second line of the street continues the first, under the same label.
                        .row(new JLabel(), street2Field)
                        .row(zipLabel, zipField)
                        .row(cityLabel, cityField)
                        .row(phoneLabel, phoneField)
                        .build(),
                BorderLayout.CENTER);
    }

    /**
     * Binds the fields to the address's parts in a form, which then tracks whether they changed.
     *
     * @param form the form
     */
    void bind(Form form) {
        form.bind(street1Field, street1);
        form.bind(street2Field, street2);
        form.bind(zipField, zip);
        form.bind(cityField, city);
        form.bind(phoneField, phone);
    }

    /**
     * Shows an address.
     *
     * @param address the address
     */
    void setAddress(Address address) {
        street1.set(address.street1());
        street2.set(address.street2());
        zip.set(address.zip());
        city.set(address.city());
        phone.set(address.phone());
        country = address.country();
    }

    /**
     * Returns the address shown.
     *
     * @return the address
     */
    Address address() {
        return new Address(street1.get(), street2.get(), zip.get(), city.get(), country, phone.get());
    }
}
