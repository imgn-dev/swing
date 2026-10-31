package be.imgn.swing.examples.addressbook;

import be.imgn.swing.Messages;

/** The messages of the address book. */
@Messages
public interface AddressBookMessages {

    /**
     * Returns the question asked before deleting a contact.
     *
     * @param firstName the contact's first name
     * @param lastName the contact's last name
     * @return the question
     */
    String confirmDelete(String firstName, String lastName);

    /**
     * Returns the message shown once a contact is deleted.
     *
     * @return the message
     */
    String deleteDone();

    /**
     * Returns the text of the picture of a contact that has none.
     *
     * @return the text
     */
    String noPicture();

    /**
     * Returns the text of the picture when no contact is selected.
     *
     * @return the text
     */
    String noContactSelected();

    /**
     * Returns the header of the column of first names.
     *
     * @return the header
     */
    String firstNameHeader();

    /**
     * Returns the header of the column of last names.
     *
     * @return the header
     */
    String lastNameHeader();

    /**
     * Returns the header of the column of home cities.
     *
     * @return the header
     */
    String homeCityHeader();

    /**
     * Returns the label of the street of an address.
     *
     * @return the label
     */
    String street();

    /**
     * Returns the label of the zip code of an address.
     *
     * @return the label
     */
    String zip();

    /**
     * Returns the label of the city of an address.
     *
     * @return the label
     */
    String city();

    /**
     * Returns the label of the phone number of an address.
     *
     * @return the label
     */
    String phone();

    /**
     * Returns the heading of a step of the wizard.
     *
     * @param step the step's number, from 1
     * @param steps the number of steps
     * @param name the step's name
     * @return the heading
     */
    String wizardStep(int step, int steps, String name);

    /**
     * Returns the name of the wizard's step of the contact's information.
     *
     * @return the name
     */
    String contactStep();

    /**
     * Returns the name of the wizard's step of the home address.
     *
     * @return the name
     */
    String homeAddressStep();

    /**
     * Returns the name of the wizard's step of the office address.
     *
     * @return the name
     */
    String officeAddressStep();

    /**
     * Returns the progress message of a test task that sleeps in chunks.
     *
     * @param chunk the chunk's number, from 1
     * @return the message
     */
    String sleepChunk(int chunk);

    /**
     * Returns the progress message of a test task that is done.
     *
     * @return the message
     */
    String taskDone();
}
