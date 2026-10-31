package be.imgn.swing.examples.addressbook;

/**
 * An address of a contact. Empty parts are empty strings.
 *
 * @param street1 the first line of the street
 * @param street2 the second line of the street
 * @param zip the zip code
 * @param city the city
 * @param country the country
 * @param phone the phone number
 */
public record Address(String street1, String street2, String zip, String city, String country, String phone) {

    /** The address with no part. */
    public static final Address EMPTY = new Address("", "", "", "", "", "");

    /**
     * Returns the address of a city.
     *
     * @param city the city
     * @return the address
     */
    public static Address ofCity(String city) {
        return new Address("", "", "", city, "", "");
    }
}
